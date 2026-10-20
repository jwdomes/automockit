package com.example.partner.b;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {

    private final JdbcClient jdbc;

    public IdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Identity> findCandidates(String pNumber, String documentNumber) {
        return jdbc.sql("""
                        select first_name, last_name, birth_date, p_number, document_number
                        from identity
                        where p_number = :pNumber or document_number = :documentNumber
                        """)
                .param("pNumber", pNumber)
                .param("documentNumber", documentNumber)
                .query(Identity.class)
                .list();
    }
}
