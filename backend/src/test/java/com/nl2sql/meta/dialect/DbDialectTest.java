package com.nl2sql.meta.dialect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DbDialectTest {

    @Test
    void mysql_type() {
        assertEquals("MYSQL", new MySqlDialect().type());
        assertEquals("POSTGRES", new PostgresDialect().type());
        assertEquals("ORACLE", new OracleDialect().type());
    }

    @Test
    void mysql_does_not_match_oracle_connection() throws Exception {
        var conn = org.mockito.Mockito.mock(java.sql.Connection.class);
        var md = org.mockito.Mockito.mock(java.sql.DatabaseMetaData.class);
        org.mockito.Mockito.when(conn.getMetaData()).thenReturn(md);
        org.mockito.Mockito.when(md.getDatabaseProductName()).thenReturn("Oracle");

        assertFalse(new MySqlDialect().matches(conn));
        assertTrue(new OracleDialect().matches(conn));
    }
}
