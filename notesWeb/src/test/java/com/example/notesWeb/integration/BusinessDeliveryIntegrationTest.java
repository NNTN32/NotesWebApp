package com.example.notesWeb.integration;

import com.example.notesWeb.platform.messaging.FailureLedger;
import com.example.notesWeb.notes.messaging.NoteUpdatePublisher;
import com.example.notesWeb.todos.reminder.application.ReminderDelivery;
import com.example.notesWeb.platform.messaging.ConfirmedPublisher;
import com.example.notesWeb.platform.messaging.OutboxRelay;
import com.example.notesWeb.platform.messaging.Inbox;
import com.example.notesWeb.notes.messaging.NoteUpdateConsumer;
import com.example.notesWeb.platform.messaging.Outbox;

import com.example.notesWeb.notes.contract.NoteRequest;
import com.example.notesWeb.notes.contract.NoteUpdateEvent;
import com.example.notesWeb.notes.contract.NoteCache;
import com.example.notesWeb.notes.application.TaskNoteService;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.rabbitmq.client.Channel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.example.notesWeb.todos.reminder.contract.ReminderDelay;
import com.example.notesWeb.auth.domain.User;
import com.example.notesWeb.auth.domain.Role;
import com.example.notesWeb.todos.domain.ListTodo;
import com.example.notesWeb.shared.id.IdGenerateRepo;
import com.example.notesWeb.auth.persistence.UserRepo;
import com.example.notesWeb.notes.persistence.NotesRepo;
import com.example.notesWeb.todos.persistence.TodoRepo;
import com.example.notesWeb.notes.application.CreateNoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class BusinessDeliveryIntegrationTest {
    static DataSource source;
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc,admin;
    String schema;
    @BeforeEach void setup() {
        String url=Objects.requireNonNull(System.getenv("MESSAGING_TEST_DB_URL"),"Use disposable test PostgreSQL");
        String user=System.getenv("MESSAGING_TEST_DB_USERNAME"),pass=System.getenv("MESSAGING_TEST_DB_PASSWORD");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,pass));
        schema="pr2business_"+UUID.randomUUID().toString().replace("-",""); admin.execute("CREATE SCHEMA "+schema);
        source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,pass);
        new ResourceDatabasePopulator(new ClassPathResource("db/messaging.sql")).execute(source);
        jdbc=new JdbcTemplate(source); context=new AnnotationConfigApplicationContext(Config.class);
    }
    @org.springframework.boot.test.context.TestConfiguration @EnableTransactionManagement
    @EnableJpaRepositories(basePackages="com.example.notesWeb")
    static class Config {
        @Bean DataSource dataSource() { return source; }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory=new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(ds);
            factory.setPackagesToScan("com.example.notesWeb"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties properties=new Properties(); properties.setProperty("hibernate.hbm2ddl.auto","create-drop");
            properties.setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
            factory.setJpaProperties(properties); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean Inbox inbox(JdbcTemplate j) { return new Inbox(j); }
        @Bean Outbox outbox(JdbcTemplate j) { return new Outbox(j); }
        @Bean IdGenerateRepo ids() { return UUID::randomUUID; }
        @Bean CreateNoteService create(Inbox inbox,UserRepo users,NotesRepo notes,IdGenerateRepo ids) { return new CreateNoteService(inbox,users,notes,ids); }
        @Bean ObjectMapper json() { return spy(new ObjectMapper().findAndRegisterModules()); }
        @Bean ReminderDelivery delivery(TodoRepo todos,Inbox inbox,Outbox outbox,JdbcTemplate j,ObjectMapper json) { return new ReminderDelivery(todos,inbox,outbox,j,json); }
        @Bean NoteUpdatePublisher updates(Outbox outbox,ObjectMapper json,JdbcTemplate j) { return new NoteUpdatePublisher(outbox,json,j); }
        @Bean TaskNoteService tasks(Inbox inbox,Outbox outbox,UserRepo users,NotesRepo notes,ObjectMapper json) {
            return new TaskNoteService(inbox,outbox,users,notes,json);
        }
        @Bean FailureLedger failures(JdbcTemplate j,Outbox outbox) { return new FailureLedger(j,outbox); }
        @Bean ConfirmedPublisher transport() { return mock(ConfirmedPublisher.class); }
        @Bean OutboxRelay relay(JdbcTemplate j,ConfirmedPublisher transport) { return new OutboxRelay(j,transport); }
    }
    @AfterEach void cleanup() { if(context!=null) context.close(); if(admin!=null) admin.execute("DROP SCHEMA "+schema+" CASCADE"); }
    User user() {
        User user=new User(); user.setId(UUID.randomUUID()); user.setUsername("alice"); user.setRole(Role.USER);
        return context.getBean(UserRepo.class).saveAndFlush(user);
    }
    @Test void actualNoteSaveAndInboxCommitOnceAcrossRedelivery() {
        user(); var service=context.getBean(CreateNoteService.class);
        service.createOnce("event-1",new NoteRequest("body","title"),"alice");
        service.createOnce("event-1",new NoteRequest("body","title"),"alice");
        assertThat(context.getBean(NotesRepo.class).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_inbox",Integer.class)).isEqualTo(1);
    }
    @Test void actualJpaCommitFailureRollsBackJdbcInboxMarker() {
        user(); var service=context.getBean(CreateNoteService.class);
        assertThatThrownBy(()->service.createOnce("event-2",new NoteRequest("body",null),"alice")).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_inbox",Integer.class)).isZero();
        service.createOnce("event-2",new NoteRequest("body","fixed"),"alice");
        assertThat(context.getBean(NotesRepo.class).count()).isEqualTo(1);
    }
    @Test void checkedSerializationFailureRollsBackReminderAndRetryCreatesOneNotification() throws Exception {
        var owner=user(); var todo=new ListTodo(); todo.setIdList(UUID.randomUUID()); todo.setHeading("task");
        todo.setUser(owner); todo.setTriggerAt(Instant.parse("2026-01-01T00:00:00.123456789Z"));
        assertThat(todo.getTriggerAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456Z"));
        var todos=context.getBean(TodoRepo.class); todo=todos.saveAndFlush(todo);
        var event=ReminderDelay.builder().todoID(todo.getIdList()).trigger(todo.getTriggerAt()).build();
        var json=context.getBean(ObjectMapper.class);
        doThrow(new JsonProcessingException("serialization failed"){}).when(json).writeValueAsString(any());
        var delivery=context.getBean(ReminderDelivery.class);
        assertThatThrownBy(()->delivery.deliver(event)).isInstanceOf(JsonProcessingException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_inbox",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reminder_notification",Integer.class)).isZero();
        doCallRealMethod().when(json).writeValueAsString(any());
        delivery.deliver(event); delivery.deliver(event);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reminder_notification",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_outbox WHERE kind='STOMP'",Integer.class)).isEqualTo(1);
        assertThat(todos.findById(todo.getIdList()).orElseThrow().getReminded()).isTrue();
    }

    NoteUpdateEvent command(String title) {
        var owner=user();
        context.getBean(CreateNoteService.class).createOnce("create-fixture",new NoteRequest("body","original"),owner.getUsername());
        var note=context.getBean(NotesRepo.class).findAll().getFirst();
        return new NoteUpdateEvent(note.getId(),owner.getId(),owner.getUsername(),new NoteRequest("edited",title));
    }
    NoteUpdateConsumer consumer() {
        return new NoteUpdateConsumer(context.getBean(TaskNoteService.class),
                context.getBean(FailureLedger.class),context.getBean(ObjectMapper.class),jdbc);
    }
    Message envelope(NoteUpdateEvent event) throws Exception {
        var properties=new MessageProperties(); properties.setDeliveryTag(1);
        return new Message(context.getBean(ObjectMapper.class).writeValueAsBytes(event),properties);
    }
    @Test void noteUpdateRollbackRetriesAndLostAckDoesNotDuplicateBusinessOrNotification() throws Exception {
        var event=command("changed"); var publisher=context.getBean(NoteUpdatePublisher.class); publisher.sendNoteUpdate(event);
        var json=context.getBean(ObjectMapper.class); var raw=envelope(event);
        doThrow(new JsonProcessingException("fail notification"){}).when(json).writeValueAsString(any(NoteCache.class));
        var channel=mock(Channel.class);
        consumer().consume(raw,channel);
        verify(channel).basicAck(1,false);
        assertThat(context.getBean(NotesRepo.class).findById(event.getNoteID()).orElseThrow().getTitle()).isEqualTo("original");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_inbox WHERE message_key LIKE 'note-update:%'",Integer.class)).isZero();
        doCallRealMethod().when(json).writeValueAsString(any(NoteCache.class));
        doThrow(new java.io.IOException("lost ACK")).when(channel).basicAck(1,false);
        assertThatThrownBy(()->consumer().consume(raw,channel)).isInstanceOf(java.io.IOException.class);
        var version=context.getBean(NotesRepo.class).findById(event.getNoteID()).orElseThrow().getVersion();
        consumer().consume(raw,mock(Channel.class));
        assertThat(context.getBean(NotesRepo.class).findById(event.getNoteID()).orElseThrow().getVersion()).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_outbox WHERE kind='STOMP'",Integer.class)).isEqualTo(1);
    }
    @Test void outOfOrderDeliveryWaitsForEarlierEditAndTerminalRedeliveryCannotOverwriteNewerEdit() throws Exception {
        var first=command("first"); var second=new NoteUpdateEvent(first.getNoteID(),first.getUserID(),first.getUsername(),new NoteRequest("body","second"));
        var publisher=context.getBean(NoteUpdatePublisher.class); publisher.sendNoteUpdate(first); publisher.sendNoteUpdate(second);
        var channel=mock(Channel.class); consumer().consume(envelope(second),channel);
        verify(channel).basicAck(1,false);
        consumer().consume(envelope(second),mock(Channel.class));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_outbox WHERE kind='RABBIT' AND destination='notes.note-updates.v1'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_attempt",Integer.class)).isZero();
        var ledger=context.getBean(FailureLedger.class); String key="note-update:"+first.getEventId();
        for(int i=0;i<5;i++) ledger.noteFailure(key,context.getBean(ObjectMapper.class).writeValueAsString(first),first.getNoteID().toString(),first.getEventId().toString());
        consumer().consume(envelope(second),mock(Channel.class));
        consumer().consume(envelope(first),mock(Channel.class));
        assertThat(context.getBean(NotesRepo.class).findById(first.getNoteID()).orElseThrow().getTitle()).isEqualTo("second");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_dead_letter WHERE message_key=?",Integer.class,key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_outbox WHERE routing_key='dead'",Integer.class)).isEqualTo(1);
    }
    @Test void rejectedOwnerNeverMutatesNoteAndCommandAcceptanceIsDurableWithoutBroker() throws Exception {
        var event=command("hijacked"); var bob=new User(); bob.setId(UUID.randomUUID()); bob.setUsername("bob"); bob.setRole(Role.USER);
        context.getBean(UserRepo.class).saveAndFlush(bob); event.setUserID(bob.getId()); event.setUsername("bob");
        context.getBean(NoteUpdatePublisher.class).sendNoteUpdate(event);
        assertThat(jdbc.queryForObject("SELECT kind FROM message_outbox WHERE id=?",String.class,event.getEventId())).isEqualTo("NOTE_UPDATE");
        consumer().consume(envelope(event),mock(Channel.class));
        assertThat(context.getBean(NotesRepo.class).findById(event.getNoteID()).orElseThrow().getTitle()).isEqualTo("original");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_inbox WHERE message_key LIKE 'note-update:%'",Integer.class)).isZero();
    }
    @Test void actualRabbitOutboxPipelineRetriesAndAppliesEditsInOrder() throws Exception {
        var first=command("first");
        var second=new NoteUpdateEvent(first.getNoteID(),first.getUserID(),first.getUsername(),new NoteRequest("body","second"));
        var third=new NoteUpdateEvent(first.getNoteID(),first.getUserID(),first.getUsername(),new NoteRequest("body","third"));
        var publisher=context.getBean(NoteUpdatePublisher.class); publisher.sendNoteUpdate(first); publisher.sendNoteUpdate(second);
        var cf=new CachingConnectionFactory(System.getenv("MESSAGING_TEST_RABBIT_HOST"),Integer.parseInt(System.getenv("MESSAGING_TEST_RABBIT_PORT")));
        cf.setUsername(System.getenv("MESSAGING_TEST_RABBIT_USERNAME")); cf.setPassword(System.getenv("MESSAGING_TEST_RABBIT_PASSWORD"));
        cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED); cf.setPublisherReturns(true);
        String name="pr3-"+UUID.randomUUID(); var admin=new RabbitAdmin(cf);
        var template=new RabbitTemplate(cf); template.setMandatory(true);
        var json=context.getBean(ObjectMapper.class);
        var transport=new ConfirmedPublisher(template,mock(SimpMessagingTemplate.class),json);
        var done=new CountDownLatch(1); var error=new AtomicReference<Throwable>();
        var applied=new CopyOnWriteArrayList<String>();
        try {
            admin.declareExchange(new TopicExchange(name,true,false));
            admin.declareQueue(QueueBuilder.durable(name).quorum().withArgument("x-single-active-consumer",true).withArgument("x-delivery-limit",-1).build());
            admin.declareBinding(new Binding(name,Binding.DestinationType.QUEUE,name,"#",null));
            doAnswer(call->{ transport.send(call.getArgument(0),call.getArgument(1),name,call.getArgument(3),call.getArgument(4)); return null; })
                    .when(context.getBean(ConfirmedPublisher.class)).send(any(),anyString(),eq("notes.note-updates.v1"),anyString(),anyString());
            doThrow(new JsonProcessingException("one transient failure"){}).doCallRealMethod().when(json).writeValueAsString(any(NoteCache.class));
            var channel=cf.createConnection().createChannel(false); channel.basicQos(1);
            var listener=consumer();
            channel.basicConsume(name,false,(tag,delivery)->{
                try {
                    var properties=new MessageProperties(); properties.setDeliveryTag(delivery.getEnvelope().getDeliveryTag());
                    listener.consume(new Message(delivery.getBody(),properties),channel);
                    int processed=jdbc.queryForObject("SELECT count(*) FROM message_inbox WHERE message_key LIKE 'note-update:%'",Integer.class);
                    if (processed>applied.size()) applied.add(context.getBean(NotesRepo.class).findById(first.getNoteID()).orElseThrow().getTitle());
                    if (processed==3) done.countDown();
                } catch(Throwable failure) { error.set(failure); done.countDown(); }
            },tag->{});
            var relay=context.getBean(OutboxRelay.class);
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            boolean thirdAccepted=false;
            while(done.getCount()>0 && System.nanoTime()<until) {
                relay.publishNext();
                if (!thirdAccepted && jdbc.queryForObject("SELECT count(*) FROM message_outbox WHERE kind='RABBIT' AND destination='notes.note-updates.v1'",Integer.class)>0) {
                    publisher.sendNoteUpdate(third); thirdAccepted=true;
                }
                Thread.sleep(25);
            }
            boolean completed=done.getCount()==0;
            assertThat(completed).withFailMessage("outbox=%s inbox=%s attempts=%s callback=%s",
                    jdbc.queryForList("SELECT kind,status,last_error FROM message_outbox"),jdbc.queryForList("SELECT * FROM message_inbox"),
                    jdbc.queryForList("SELECT * FROM message_attempt"),error.get()).isTrue(); assertThat(error.get()).isNull();
            assertThat(applied).containsExactly("first","second","third");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM message_outbox WHERE kind='STOMP'",Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT attempts FROM message_attempt WHERE message_key=?",Integer.class,"note-update:"+first.getEventId())).isEqualTo(1);
            channel.close();
        } finally {
            try { admin.deleteQueue(name); admin.deleteExchange(name); } finally { cf.destroy(); }
        }
    }
}
