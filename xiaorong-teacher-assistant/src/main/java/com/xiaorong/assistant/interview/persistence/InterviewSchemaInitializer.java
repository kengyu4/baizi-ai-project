package com.xiaorong.assistant.interview.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Creates only the tables owned by the interview module when MySQL persistence is enabled. */
@Component
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
public class InterviewSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public InterviewSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void run(ApplicationArguments args) {
        jdbc.execute("""
                create table if not exists ai_interviewer (
                  id bigint primary key auto_increment,
                  code varchar(50) not null unique,
                  name varchar(50) not null,
                  specialty varchar(500), style varchar(500), avatar varchar(500), supported_positions json,
                  enabled tinyint default 1, create_time datetime, update_time datetime
                )
                """);
        jdbc.execute("""
                create table if not exists ai_interview_resume (
                  id bigint primary key auto_increment, user_id bigint not null, title varchar(150) not null,
                  position_name varchar(100) not null, content_text longtext not null, file_name varchar(255), file_type varchar(100),
                  file_size bigint, parse_status varchar(20) not null, parse_error varchar(500), create_time datetime, update_time datetime,
                  key idx_interview_resume_user(user_id)
                )
                """);
        jdbc.execute("""
                create table if not exists ai_interview_session (
                  id bigint primary key auto_increment, user_id bigint not null, interviewer_id bigint not null, resume_id bigint,
                  interviewer_code varchar(50) not null, interviewer_name varchar(50) not null, position_name varchar(100) not null,
                  subject_name varchar(100), status varchar(20) not null, current_question_index int default 0, question_json json,
                  total_score int default 0, weak_tags json, report_json json, started_at datetime, finished_at datetime,
                  create_time datetime, update_time datetime, key idx_interview_session_user(user_id)
                )
                """);
        addColumnIfMissing("ai_interview_session", "question_count", "int default 3");
        addColumnIfMissing("ai_interview_session", "self_intro_text", "longtext");
        addColumnIfMissing("ai_interview_session", "self_intro_feedback_json", "json");
        addColumnIfMissing("ai_interview_session", "interview_context_json", "json");
        jdbc.execute("""
                create table if not exists ai_interview_record (
                  id bigint primary key auto_increment, session_id bigint not null, question_index int not null, topic_name text not null,
                  key_hint varchar(500), keywords json, answer_summary text, user_answer longtext, follow_up_questions json,
                  follow_up_answers json, follow_up_level int default 0, score int, level varchar(20), hit_keywords json,
                  miss_keywords json, interviewer_comment text, risk text, ai_generated tinyint, provider_code varchar(100),
                  model varchar(150), create_time datetime, unique key uk_interview_record_question(session_id, question_index),
                  key idx_interview_record_session(session_id)
                )
                """);
        jdbc.update("""
                insert into ai_interviewer(code,name,specialty,style,avatar,supported_positions,enabled,create_time,update_time)
                values ('lanchuan','岚川','通用求职','冷静专业，逐题追问并给出可执行建议',
                '/assets/characters/interviewer-idle.png','[\"全部岗位\"]',1,now(),now())
                on duplicate key update name=values(name),specialty=values(specialty),style=values(style),avatar=values(avatar),
                supported_positions=values(supported_positions),enabled=1,update_time=now()
                """);
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = ? and column_name = ?
                """, Integer.class, table, column);
        if (count == null || count == 0) jdbc.execute("alter table " + table + " add column " + column + " " + definition);
    }
}
