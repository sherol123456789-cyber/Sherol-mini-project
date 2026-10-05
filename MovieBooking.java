import java.sql.*;
import java.util.*;

public class MovieBooking {
    static Scanner sc = new Scanner(System.in);
    static final String[] SEATS = {
        "A1","A2","A3","A4","A5","B1","B2","B3","B4","B5",
        "C1","C2","C3","C4","C5","D1","D2","D3","D4","D5"
    };

    public static void main(String[] args) {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (ClassNotFoundException e) {
            System.out.println("Add MySQL Connector/J to the project.");
            return;
        }

        while (true) {
            System.out.println("\n====================================");
            System.out.println("      MOVIE TICKET BOOKING SYSTEM");
            System.out.println("====================================");
            System.out.println("1. View Movies");
            System.out.println("2. Search Movie");
            System.out.println("3. Book Ticket");
            System.out.println("4. View Booking");
            System.out.println("5. Cancel Booking");
            System.out.println("6. Exit");
            System.out.print("Enter choice: ");
            String ch = sc.nextLine();

            switch (ch) {
                case "1" -> showMovies("");
                case "2" -> { System.out.print("Search: "); showMovies(sc.nextLine()); }
                case "3" -> bookTicket();
                case "4" -> viewBooking();
                case "5" -> cancelBooking();
                case "6" -> { System.out.println("Thank you!"); return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    static void showMovies(String key) {
        String sql = "SELECT * FROM movies WHERE movie_name LIKE ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, "%" + key + "%");
            ResultSet r = p.executeQuery();
            System.out.printf("%n%-3s %-28s %-14s %-10s %-8s%n",
                    "ID","Movie","Genre","Duration","Price");
            while (r.next())
                System.out.printf("%-3d %-28s %-14s %-10s Rs.%-8.2f%n",
                    r.getInt("movie_id"), r.getString("movie_name"),
                    r.getString("genre"), r.getString("duration"),
                    r.getDouble("price"));
        } catch (SQLException e) { System.out.println(e.getMessage()); }
    }

    static void bookTicket() {
        showMovies("");
        System.out.print("Movie ID: ");
        int movieId = Integer.parseInt(sc.nextLine());
        Movie m = getMovie(movieId);
        if (m == null) { System.out.println("Movie not found."); return; }

        System.out.print("Customer name: ");
        String name = sc.nextLine();
        System.out.print("Phone: ");
        String phone = sc.nextLine();

        Set<String> booked = bookedSeats(movieId);
        showSeats(booked);
        System.out.print("Seats (example A1,A2): ");
        String[] input = sc.nextLine().toUpperCase().replace(" ","").split(",");
        List<String> selected = new ArrayList<>();

        for (String s : input)
            if (validSeat(s) && !booked.contains(s) && !selected.contains(s)) selected.add(s);

        if (selected.isEmpty()) { System.out.println("No valid seats selected."); return; }

        System.out.println("1. Regular  Rs." + m.price);
        System.out.println("2. Premium  Rs." + (m.price + 50));
        System.out.println("3. VIP      Rs." + (m.price + 100));
        System.out.print("Category: ");
        int cat = Integer.parseInt(sc.nextLine());

        String category;
        double seatPrice;
        if (cat == 1) { category="Regular"; seatPrice=m.price; }
        else if (cat == 2) { category="Premium"; seatPrice=m.price+50; }
        else if (cat == 3) { category="VIP"; seatPrice=m.price+100; }
        else { System.out.println("Invalid category."); return; }

        System.out.println("1. None Rs.0");
        System.out.println("2. Popcorn Rs.80");
        System.out.println("3. Popcorn + Coke Rs.120");
        System.out.print("Food combo: ");
        int foodChoice = Integer.parseInt(sc.nextLine());

        String food;
        double foodPrice;
        if (foodChoice == 1) { food="None"; foodPrice=0; }
        else if (foodChoice == 2) { food="Popcorn"; foodPrice=80; }
        else if (foodChoice == 3) { food="Popcorn + Coke"; foodPrice=120; }
        else { System.out.println("Invalid food choice."); return; }

        double ticketTotal = seatPrice * selected.size();
        double total = ticketTotal + foodPrice;

        System.out.println("Ticket total: Rs." + ticketTotal);
        System.out.println("Food total: Rs." + foodPrice);
        System.out.println("TOTAL: Rs." + total);
        System.out.print("Confirm (Y/N): ");
        if (!sc.nextLine().equalsIgnoreCase("Y")) return;

        saveBooking(name, phone, m, selected, category, food, ticketTotal, foodPrice, total);
    }

    static void saveBooking(String name,String phone,Movie m,List<String> seats,
                            String category,String food,double ticket,double foodTotal,double total) {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            int customerId;
            try (PreparedStatement p=c.prepareStatement(
                    "INSERT INTO customers(customer_name,phone) VALUES(?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1,name); p.setString(2,phone); p.executeUpdate();
                ResultSet k=p.getGeneratedKeys(); k.next(); customerId=k.getInt(1);
            }

            int bookingId;
            try (PreparedStatement p=c.prepareStatement(
                    "INSERT INTO bookings(customer_id,movie_id,seats,seat_category,food_combo,ticket_total,food_total,total_price) VALUES(?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setInt(1,customerId); p.setInt(2,m.id); p.setString(3,String.join(", ",seats));
                p.setString(4,category); p.setString(5,food); p.setDouble(6,ticket);
                p.setDouble(7,foodTotal); p.setDouble(8,total); p.executeUpdate();
                ResultSet k=p.getGeneratedKeys(); k.next(); bookingId=k.getInt(1);
            }
            c.commit();

            System.out.println("\n========== BOOKING CONFIRMED ==========");
            System.out.println("Booking ID: BK"+bookingId);
            System.out.println("Customer: "+name);
            System.out.println("Movie: "+m.name);
            System.out.println("Seats: "+String.join(", ",seats));
            System.out.println("Category: "+category);
            System.out.println("Food: "+food);
            System.out.println("TOTAL: Rs."+total);
            System.out.println("========================================");
        } catch(SQLException e) { System.out.println("Booking failed: "+e.getMessage()); }
    }

    static void viewBooking() {
        System.out.print("Booking ID (without BK): ");
        int id=Integer.parseInt(sc.nextLine());
        String sql="SELECT b.*,c.customer_name,c.phone,m.movie_name FROM bookings b "+
                   "JOIN customers c ON b.customer_id=c.customer_id "+
                   "JOIN movies m ON b.movie_id=m.movie_id WHERE b.booking_id=?";
        try(Connection c=DBConnection.getConnection(); PreparedStatement p=c.prepareStatement(sql)){
            p.setInt(1,id); ResultSet r=p.executeQuery();
            if(r.next()){
                System.out.println("\nBooking BK"+r.getInt("booking_id"));
                System.out.println("Customer: "+r.getString("customer_name"));
                System.out.println("Movie: "+r.getString("movie_name"));
                System.out.println("Seats: "+r.getString("seats"));
                System.out.println("Category: "+r.getString("seat_category"));
                System.out.println("Food: "+r.getString("food_combo"));
                System.out.println("Total: Rs."+r.getDouble("total_price"));
                System.out.println("Date: "+r.getTimestamp("booking_date"));
            } else System.out.println("Booking not found.");
        } catch(SQLException e){System.out.println(e.getMessage());}
    }

    static void cancelBooking() {
        System.out.print("Booking ID (without BK): ");
        int id=Integer.parseInt(sc.nextLine());
        try(Connection c=DBConnection.getConnection(); PreparedStatement p=c.prepareStatement(
                "DELETE FROM bookings WHERE booking_id=?")){
            p.setInt(1,id);
            System.out.println(p.executeUpdate()>0 ? "Booking cancelled." : "Booking not found.");
        }catch(SQLException e){System.out.println(e.getMessage());}
    }

    static Movie getMovie(int id) {
        try(Connection c=DBConnection.getConnection(); PreparedStatement p=c.prepareStatement(
                "SELECT * FROM movies WHERE movie_id=?")){
            p.setInt(1,id); ResultSet r=p.executeQuery();
            if(r.next()) return new Movie(r.getInt("movie_id"),r.getString("movie_name"),r.getDouble("price"));
        }catch(SQLException e){System.out.println(e.getMessage());}
        return null;
    }

    static Set<String> bookedSeats(int movieId) {
        Set<String> set=new HashSet<>();
        try(Connection c=DBConnection.getConnection(); PreparedStatement p=c.prepareStatement(
                "SELECT seats FROM bookings WHERE movie_id=?")){
            p.setInt(1,movieId); ResultSet r=p.executeQuery();
            while(r.next()) for(String s:r.getString("seats").split(",")) set.add(s.trim());
        }catch(SQLException e){System.out.println(e.getMessage());}
        return set;
    }

    static void showSeats(Set<String> booked) {
        System.out.println("\n                 SCREEN");
        System.out.println("--------------------------------");
        for(int row=0;row<4;row++){
            for(int col=1;col<=5;col++){
                String s=""+(char)('A'+row)+col;
                System.out.print(booked.contains(s) ? "[X] " : "[O] ");
            }
            System.out.println();
        }
        System.out.println("[O] Available   [X] Booked");
    }

    static boolean validSeat(String s){ for(String x:SEATS) if(x.equals(s)) return true; return false; }

    static class Movie {
        int id; String name; double price;
        Movie(int id,String name,double price){this.id=id;this.name=name;this.price=price;}
    }
}
