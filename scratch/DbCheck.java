package com.lamb.springaiknowledgeserver.scratch;

import java.sql.*;
import java.util.*;

public class DbCheck {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://47.109.80.120:5432/springai_knowledge";
        String user = "postgres";
        String password = System.getenv("PG_PASSWORD");
        
        if (password == null) {
            System.out.println("PG_PASSWORD not found in env");
            return;
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to DB");
            
            // Check ADMIN role permissions
            String sql = "SELECT p.permission FROM app_role_permission p " +
                         "JOIN app_role r ON p.role_id = r.id " +
                         "WHERE r.name = 'ADMIN'";
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                System.out.println("ADMIN Permissions:");
                while (rs.next()) {
                    System.out.println(" - " + rs.getString("permission"));
                }
            }
            
            // Check users and roles
            sql = "SELECT username, r.name as role_name FROM app_user u " +
                  "LEFT JOIN app_role r ON u.role_id = r.id";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                System.out.println("Users:");
                while (rs.next()) {
                    System.out.println(" - " + rs.getString("username") + " (" + rs.getString("role_name") + ")");
                }
            }
        }
    }
}
