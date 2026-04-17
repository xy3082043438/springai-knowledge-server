import java.sql.*;

public class DbCheck {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/springai_knowledge";
        String user = "postgres";
        String password = System.getenv("PG_PASSWORD");
        
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to Postgres!");
            
            try (Statement stmt = conn.createStatement()) {
                System.out.println("Checking app_qa_log...");
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM app_qa_log");
                if (rs.next()) System.out.println("Total QA Logs: " + rs.getInt(1));
                
                System.out.println("Checking sessions...");
                rs = stmt.executeQuery("SELECT count(*) FROM app_chat_session");
                if (rs.next()) System.out.println("Total Sessions: " + rs.getInt(1));
                
                System.out.println("First 5 logs:");
                rs = stmt.executeQuery("SELECT id, user_id, session_id, question FROM app_qa_log LIMIT 5");
                while (rs.next()) {
                    System.out.printf("ID: %d, UID: %d, SID: %d, Q: %s%n", 
                        rs.getLong("id"), rs.getLong("user_id"), rs.getLong("session_id"), rs.getString("question"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
