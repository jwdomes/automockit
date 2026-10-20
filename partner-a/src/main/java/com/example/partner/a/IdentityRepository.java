package com.example.partner.a;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {

    private final JdbcClient jdbc;

    public IdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Identity> findCandidates(String ssn, String documentNumber) {
        return jdbc.sql("""
                        select first_name, last_name, birth_date, ssn, document_number
                        from identity
                        where ssn = :ssn or document_number = :documentNumber
                        """)
                .param("ssn", ssn)
                .param("documentNumber", documentNumber)
                .query(Identity.class)
                .list();
    }
}
