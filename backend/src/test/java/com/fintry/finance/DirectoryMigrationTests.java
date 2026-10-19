package com.fintry.finance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DirectoryMigrationTests {
    @Test void upgradesExistingDatabaseWithoutRemovingEntries() throws Exception {
        String url="jdbc:h2:mem:migration-"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try(var pool=new com.zaxxer.hikari.HikariDataSource()) {
        pool.setJdbcUrl(url);pool.setUsername("sa");pool.setPassword("");pool.setMaximumPoolSize(3);
        Flyway.configure().dataSource(pool).locations("classpath:db/demo").target("1").load().migrate();
        UUID company=UUID.randomUUID(),account=UUID.randomUUID(),entry=UUID.randomUUID();
        try(var conn=pool.getConnection();var stmt=conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO companies(id,legal_name,document,created_at,updated_at) VALUES('"+company+"','Empresa existente','12345678000190',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            stmt.executeUpdate("INSERT INTO financial_accounts(id,company_id,name,initial_balance,created_at,updated_at) VALUES('"+account+"','"+company+"','Banco',500,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            stmt.executeUpdate("INSERT INTO financial_entries(id,company_id,account_id,description,category,type,amount,due_date,status,created_at,updated_at) VALUES('"+entry+"','"+company+"','"+account+"','Lançamento existente','Categoria avulsa','INCOME',100,CURRENT_DATE,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        }
        Flyway.configure().dataSource(pool).locations("classpath:db/demo").load().migrate();
        try(var conn=pool.getConnection();var stmt=conn.createStatement();var rs=stmt.executeQuery("SELECT category,amount,category_id,contact_id FROM financial_entries WHERE id='"+entry+"'")) {
            assertTrue(rs.next());assertEquals("Categoria avulsa",rs.getString(1));assertEquals(100,rs.getBigDecimal(2).intValueExact());assertNull(rs.getObject(3));assertNull(rs.getObject(4));
        }
        }
    }
}
