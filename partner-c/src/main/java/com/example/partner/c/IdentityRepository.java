package com.example.partner.c;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {

    private final JdbcClient jdbc;

    public IdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Identity> findCandidates(String ssn) {
        return jdbc.sql("""
                        select first_name, last_name, birth_date, ssn
                        from identity
                        where ssn = :ssn
                        """)
                .param("ssn", ssn)
                .query(Identity.class)
                .list();
    }
}
