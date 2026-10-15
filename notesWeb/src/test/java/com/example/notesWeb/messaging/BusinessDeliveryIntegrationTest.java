package com.example.notesWeb.messaging;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.dtos.TodoListDto.notificatePopup.ReminderDelay;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.Role;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.repository.*;
import com.example.notesWeb.repository.noteRepo.*;
import com.example.notesWeb.repository.todoRepo.TodoRepo;
import com.example.notesWeb.service.takeNotes.CreateNoteService;
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
    @EnableJpaRepositories(basePackages="com.example.notesWeb.repository")
    static class Config {
        @Bean DataSource dataSource() { return source; }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory=new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(ds);
            factory.setPackagesToScan("com.example.notesWeb.model"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
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
        todo.setUser(owner); todo.setTriggerAt(Instant.now().minusSeconds(60));
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
}
