package com.example.notesWeb.platform.messaging;

import com.example.notesWeb.auth.messaging.AuthResults;

import com.example.notesWeb.platform.redis.RedisStreamConsume;
import com.example.notesWeb.platform.redis.RedisConfig;
import com.example.notesWeb.auth.contract.AuthResponse;
import com.example.notesWeb.auth.domain.Status;
import com.example.notesWeb.auth.domain.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessagingIntegrationTest {
    static DriverManagerDataSource source;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static String schema;
    static DriverManagerDataSource admin;
    static String required(String key) { return Objects.requireNonNull(System.getenv(key),"Set disposable test infrastructure variable: "+key); }
    @BeforeAll static void setup() {
        String url=required("MESSAGING_TEST_DB_URL");
        String user=required("MESSAGING_TEST_DB_USERNAME"), password=required("MESSAGING_TEST_DB_PASSWORD");
        schema="pr2_"+UUID.randomUUID().toString().replace("-","");
        admin=new DriverManagerDataSource(url,user,password);
        new JdbcTemplate(admin).execute("CREATE SCHEMA "+schema);
        source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
        jdbc=new JdbcTemplate(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/messaging.sql")).execute(source);
        jdbc.execute("CREATE TABLE business_fixture(id VARCHAR(100) PRIMARY KEY, value INTEGER NOT NULL)");
        restart();
    }
    static void restart() {
        if (context!=null) context.close();
        context=new AnnotationConfigApplicationContext(Config.class);
        tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }
    @org.springframework.boot.test.context.TestConfiguration @EnableTransactionManagement static class Config {
        @Bean DataSource dataSource() { return source; }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactions(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean Inbox inbox(JdbcTemplate j) { return new Inbox(j); }
        @Bean Outbox outbox(JdbcTemplate j) { return new Outbox(j); }
        @Bean FailureLedger failures(JdbcTemplate j,Outbox outbox) { return new FailureLedger(j,outbox); }
        @Bean ConfirmedPublisher publisher() { return mock(ConfirmedPublisher.class); }
        @Bean OutboxRelay relay(JdbcTemplate j,ConfirmedPublisher p) { return new OutboxRelay(j,p); }
    }
    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE message_outbox,message_inbox,message_attempt,message_dead_letter,reminder_notification,business_fixture");
        reset(context.getBean(ConfirmedPublisher.class));
    }
    @AfterAll static void cleanup() {
        if(context!=null) context.close();
        if(admin!=null) new JdbcTemplate(admin).execute("DROP SCHEMA "+schema+" CASCADE");
    }
    Inbox inbox() { return context.getBean(Inbox.class); }
    Outbox outbox() { return context.getBean(Outbox.class); }
    OutboxRelay relay() { return context.getBean(OutboxRelay.class); }
    FailureLedger failures() { return context.getBean(FailureLedger.class); }
    UUID append(String route) {
        UUID id=UUID.randomUUID();
        tx.executeWithoutResult(s->outbox().append(id,"RABBIT","test",route,"{}",Instant.now()));
        return id;
    }
    long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class); }
    @Test void outboxRequiresBusinessTransactionAndRollsBackWithIt() {
        assertThatThrownBy(()->outbox().append(UUID.randomUUID(),"RABBIT","test","route","{}",Instant.now())).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(()->tx.executeWithoutResult(s->{
            jdbc.update("INSERT INTO business_fixture VALUES ('note',1)");
            outbox().append(UUID.randomUUID(),"RABBIT","test","route","{}",Instant.now());
            throw new IllegalStateException("Crash before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("business_fixture")).isZero(); assertThat(count("message_outbox")).isZero();
    }
    @Test void inboxAndBusinessWriteRollbackAndDeduplicateAcrossRestart() {
        assertThatThrownBy(()->tx.executeWithoutResult(s->{ inbox().claim("job");
            jdbc.update("INSERT INTO business_fixture VALUES ('note',1)"); throw new IllegalStateException();
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("message_inbox")).isZero();
        tx.executeWithoutResult(s->{ assertThat(inbox().claim("job")).isTrue(); jdbc.update("INSERT INTO business_fixture VALUES ('note',1)"); });
        restart();
        tx.executeWithoutResult(s->assertThat(inbox().claim("job")).isFalse());
        assertThat(count("business_fixture")).isEqualTo(1);
    }
    @Test void uncertainPublishRetainsPayloadForRetry() throws Exception {
        UUID id=append("route");
        doThrow(new IllegalStateException("Timeout after broker received it")).when(context.getBean(ConfirmedPublisher.class))
                .send(any(),anyString(),anyString(),anyString(),anyString());
        relay().publishNext();
        assertThat(jdbc.queryForObject("SELECT status FROM message_outbox WHERE id=?",String.class,id)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM message_outbox WHERE id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload FROM message_outbox WHERE id=?",String.class,id)).isEqualTo("{}");
    }
    @Test void crashAfterBrokerAcceptanceReplaysSameEnvelopeId() throws Exception {
        UUID id=append("route"); var publisher=context.getBean(ConfirmedPublisher.class);
        doThrow(new AssertionError("Process crash after send")).when(publisher).send(any(),anyString(),anyString(),anyString(),anyString());
        assertThatThrownBy(()->relay().publishNext()).isInstanceOf(AssertionError.class);
        assertThat(jdbc.queryForObject("SELECT status FROM message_outbox WHERE id=?",String.class,id)).isEqualTo("PENDING");
        doNothing().when(publisher).send(any(),anyString(),anyString(),anyString(),anyString());
        relay().publishNext(); verify(publisher,times(2)).send(eq(id),anyString(),anyString(),anyString(),anyString());
        assertThat(jdbc.queryForObject("SELECT status FROM message_outbox WHERE id=?",String.class,id)).isEqualTo("PUBLISHED");
    }
    @Test void concurrentRelaysSkipLockedRows() throws Exception {
        UUID first=append("first"); append("second");
        CountDownLatch locked=new CountDownLatch(1), release=new CountDownLatch(1);
        doAnswer(call->{ if(first.equals(call.getArgument(0))) { locked.countDown(); assertThat(release.await(10,TimeUnit.SECONDS)).isTrue(); } return null; })
                .when(context.getBean(ConfirmedPublisher.class)).send(any(),anyString(),anyString(),anyString(),anyString());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a=pool.submit(()->relay().publishNext()); assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(pool.submit(()->relay().publishNext()).get(5,TimeUnit.SECONDS)).isTrue();
            release.countDown(); assertThat(a.get(5,TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM message_outbox WHERE status='PUBLISHED'",Integer.class)).isEqualTo(2);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void failedEarlierCommandBlocksOnlyItsOwnNote() throws Exception {
        UUID first=UUID.randomUUID(), second=UUID.randomUUID(), unrelated=UUID.randomUUID();
        tx.executeWithoutResult(t->{
            outbox().append(first,"NOTE_UPDATE","notes.note-updates.v1","note-a","{}",Instant.now().minusSeconds(2));
            outbox().append(second,"NOTE_UPDATE","notes.note-updates.v1","note-a","{}",Instant.now().minusSeconds(1));
            outbox().append(unrelated,"NOTE_UPDATE","notes.note-updates.v1","note-b","{}",Instant.now());
        });
        var publisher=context.getBean(ConfirmedPublisher.class);
        doThrow(new IllegalStateException("broker down")).when(publisher).send(eq(first),anyString(),anyString(),anyString(),anyString());
        relay().publishNext(); relay().publishNext();
        assertThat(relay().publishNext()).isFalse();
        verify(publisher,never()).send(eq(second),anyString(),anyString(),anyString(),anyString());
        verify(publisher).send(eq(unrelated),anyString(),anyString(),anyString(),anyString());
        doNothing().when(publisher).send(eq(first),anyString(),anyString(),anyString(),anyString());
        jdbc.update("UPDATE message_outbox SET available_at=now()-interval '3 seconds' WHERE id=?",first);
        relay().publishNext(); relay().publishNext();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_outbox WHERE status='PUBLISHED'",Integer.class)).isEqualTo(3);
    }
    @Test void concurrentInboxClaimsCommitExactlyOneBusinessWrite() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> work=()->tx.execute(t->{
            if(!inbox().claim("concurrent")) return false;
            jdbc.update("INSERT INTO business_fixture VALUES ('once',1)"); return true;
        });
        try {
            var a=pool.submit(work); var b=pool.submit(work);
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(count("business_fixture")).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void exhaustedRetryHasDurableDlqAndRedisStoresOnlyOriginalReference() {
        for(int i=0;i<5;i++) failures().rabbitFailure("bad-rabbit","{\"todoID\":\"bad\"}","Invalid envelope");
        assertThat(count("message_dead_letter")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_outbox WHERE routing_key='dead'",Integer.class)).isEqualTo(1);
        for(int i=0;i<4;i++) assertThat(failures().redisFailure("auth-failure","auth:login:stream","12-0")).isFalse();
        assertThat(failures().redisFailure("auth-failure","auth:login:stream","12-0")).isTrue();
        assertThat(jdbc.queryForObject("SELECT payload FROM message_dead_letter WHERE message_key='auth-failure'",String.class)).isEqualTo("12-0");
    }
    @Test void kafkaCutoverPreservesPendingPayloadIdentityAndPublishedHistory() throws Exception {
        UUID pending=UUID.randomUUID(),published=UUID.randomUUID(),note=UUID.randomUUID();
        var event=new com.example.notesWeb.notes.contract.NoteUpdateEvent(note,UUID.randomUUID(),"alice",new com.example.notesWeb.notes.contract.NoteRequest("body","title"));
        event.setEventId(pending); String payload=new ObjectMapper().writeValueAsString(event);
        tx.executeWithoutResult(s->{
            outbox().append(pending,"KAFKA","note-updates",note.toString(),payload,Instant.now());
            outbox().append(published,"KAFKA","note-updates",note.toString(),"history",Instant.now());
        });
        jdbc.update("UPDATE message_outbox SET status='PUBLISHED' WHERE id=?",published);
        assertThatThrownBy(()->new MessagingConfig().messagingSchema(source,false).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        String script=new String(new ClassPathResource("db/migrate-note-updates-to-rabbit.sql").getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        jdbc.execute(script); jdbc.execute(script);
        assertThat(jdbc.queryForObject("SELECT payload FROM message_outbox WHERE id=?",String.class,pending)).isEqualTo(payload);
        assertThat(jdbc.queryForObject("SELECT kind FROM message_outbox WHERE id=?",String.class,pending)).isEqualTo("NOTE_UPDATE");
        assertThat(jdbc.queryForObject("SELECT kind FROM message_outbox WHERE id=?",String.class,published)).isEqualTo("KAFKA");
        new MessagingConfig().messagingSchema(source,false).afterPropertiesSet();
    }
    @Test void invalidLegacyCutoverFailsWithoutPartiallyConvertingCommands() throws Exception {
        UUID id=UUID.randomUUID();
        var valid=new com.example.notesWeb.notes.contract.NoteUpdateEvent(UUID.randomUUID(),UUID.randomUUID(),"alice",new com.example.notesWeb.notes.contract.NoteRequest("body","valid"));
        String payload=new ObjectMapper().writeValueAsString(valid);
        tx.executeWithoutResult(s->{
            outbox().append(valid.getEventId(),"KAFKA","note-updates",valid.getNoteID().toString(),payload,Instant.now());
            outbox().append(id,"KAFKA","note-updates","note","{}",Instant.now());
        });
        String script=new String(new ClassPathResource("db/migrate-note-updates-to-rabbit.sql").getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(()->jdbc.execute(script)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT kind FROM message_outbox WHERE id=?",String.class,id)).isEqualTo("KAFKA");
        assertThat(jdbc.queryForObject("SELECT kind FROM message_outbox WHERE id=?",String.class,valid.getEventId())).isEqualTo("KAFKA");
    }
    @Test void actualRabbitConfirmsReturnsAndRedeliversAfterLostAck() throws Exception {
        var cf=new CachingConnectionFactory(required("MESSAGING_TEST_RABBIT_HOST"),Integer.parseInt(required("MESSAGING_TEST_RABBIT_PORT")));
        cf.setUsername(required("MESSAGING_TEST_RABBIT_USERNAME")); cf.setPassword(required("MESSAGING_TEST_RABBIT_PASSWORD"));
        cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED); cf.setPublisherReturns(true);
        String name="pr2-"+UUID.randomUUID(); var rabbitAdmin=new RabbitAdmin(cf);
        var template=new RabbitTemplate(cf); template.setMandatory(true);
        try {
            rabbitAdmin.declareExchange(new DirectExchange(name));
            rabbitAdmin.declareQueue(QueueBuilder.durable(name).quorum().build());
            rabbitAdmin.declareBinding(new Binding(name,Binding.DestinationType.QUEUE,name,"ready",null));
            var publisher=new ConfirmedPublisher(template,mock(SimpMessagingTemplate.class),new ObjectMapper());
            assertThatThrownBy(()->publisher.send(UUID.randomUUID(),"RABBIT",name,"missing","{}")).isInstanceOf(IllegalStateException.class);
            UUID id=UUID.randomUUID(); publisher.send(id,"RABBIT",name,"ready","{}");
            var connection=cf.createConnection(); var first=connection.createChannel(false);
            var received=first.basicGet(name,false); assertThat(received).isNotNull();
            tx.executeWithoutResult(s->{ assertThat(inbox().claim(id.toString())).isTrue(); jdbc.update("INSERT INTO business_fixture VALUES ('delivered',1)"); });
            cf.resetConnection(); // physical connection loss after commit, before ACK
            var second=cf.createConnection().createChannel(false); com.rabbitmq.client.GetResponse replay=null;
            for(int i=0;i<50 && replay==null;i++) { replay=second.basicGet(name,false); if(replay==null) Thread.sleep(20); }
            assertThat(replay).isNotNull();
            replay=java.util.Objects.requireNonNull(replay, "Rabbit message must be redelivered after the lost ACK");
            assertThat(replay.getProps().getMessageId()).isEqualTo(id.toString());
            tx.executeWithoutResult(s->assertThat(inbox().claim(id.toString())).isFalse());
            second.basicAck(replay.getEnvelope().getDeliveryTag(),false); second.close();
            assertThat(count("business_fixture")).isEqualTo(1);
        } finally { rabbitAdmin.deleteQueue(name); rabbitAdmin.deleteExchange(name); cf.destroy(); }
    }
    @Test void actualRedisReclaimsPendingAndAuthResultIsAtomicWithTtl() {
        var config=new RedisStandaloneConfiguration("localhost",Integer.parseInt(required("MESSAGING_TEST_REDIS_PORT")));
        var factory=new LettuceConnectionFactory(config); factory.afterPropertiesSet(); factory.start();
        var redis=new RedisConfig().redisTemplate(factory); String stream="pr2:"+UUID.randomUUID();
        String session=UUID.randomUUID().toString();
        try {
            var groups=new com.example.notesWeb.platform.redis.RedisStreamGroup(redis);
            ReflectionTestUtils.invokeMethod(groups,"createGroup",stream+":empty","test");
            ReflectionTestUtils.invokeMethod(groups,"createGroup",stream+":empty","test");
            assertThat(redis.opsForStream().size(stream+":empty")).isZero();
            var id=redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(Map.<Object,Object>of("value","payload")));
            redis.opsForStream().createGroup(stream,ReadOffset.from("0-0"),"test");
            var records=redis.<Object,Object>boundStreamOps(stream).read(Consumer.from("test","crashed"),StreamReadOptions.empty().count(1),ReadOffset.lastConsumed());
            assertThat(records).hasSize(1);
            tx.executeWithoutResult(s->{ inbox().claim("redis:"+id); jdbc.update("INSERT INTO business_fixture VALUES ('redis',1)"); });
            redis.execute((RedisCallback<Object>)c->c.execute("XCLAIM",stream.getBytes(StandardCharsets.UTF_8),"test".getBytes(StandardCharsets.UTF_8),"crashed".getBytes(StandardCharsets.UTF_8),"0".getBytes(StandardCharsets.UTF_8),id.getValue().getBytes(StandardCharsets.UTF_8),"IDLE".getBytes(StandardCharsets.UTF_8),"31000".getBytes(StandardCharsets.UTF_8)));
            var consumer=new RedisStreamConsume(redis,stream,"test",failures()) {
                @Override protected void handleMessage(MapRecord<String,Object,Object> record) {
                    tx.executeWithoutResult(s-> { if(inbox().claim("redis:"+record.getId())) jdbc.update("INSERT INTO business_fixture VALUES ('duplicate',1)"); });
                }
            };
            ReflectionTestUtils.invokeMethod(consumer,"recoverPending","restarted"); consumer.shutdown();
            assertThat(redis.opsForStream().pending(stream,"test").getTotalPendingMessages()).isZero();
            assertThat(count("business_fixture")).isEqualTo(1);
            var auth=new AuthResults(redis); String lease=auth.claim(session);
            auth.complete(session,lease,new AuthResponse("access","rotation",UUID.randomUUID(),"alice",Role.USER,Status.SUCCESS,"Login successful"));
            assertThat(redis.opsForHash().entries("login:result:"+session)).containsEntry("status","SUCCESS");
            assertThat(redis.getExpire("login:result:"+session)).isBetween(1L,120L);
            redis.delete("login:result:"+session); assertThat(auth.completed(session)).isTrue();
            auth.release(session,lease);
        } finally { redis.delete(List.of(stream,stream+":empty","login:result:"+session,"login:completed:"+session,"login:lease:"+session)); factory.destroy(); }
    }
}
