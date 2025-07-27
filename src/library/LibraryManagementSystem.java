package library;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class LibraryManagementSystem {
    // Database connection details
    private static final String URL = "jdbc:mysql://localhost:3306/library_db";
    private static final String USER = "root";
    private static final String PASSWORD = "Pr@mOd#8310";

    // GUI components
    private static JFrame frame;
    private static JTextField userIdField, passwordField, titleField, authorField, copiesField, searchField, issueBookIdField, issueUserIdField, returnBookIdField, returnUserIdField;
    private static JTable bookTable;
    private static DefaultTableModel tableModel;
    private static boolean isAdmin = false;
    private static String loggedInUser = null;

    // Static inner class for Book
    static class Book {
        private int bookId;
        private String title;
        private String author;
        private int totalCopies;
        private int availableCopies;

        Book(String title, String author, int totalCopies) {
            this.title = title;
            this.author = author;
            this.totalCopies = totalCopies;
            this.availableCopies = totalCopies;
        }

        public int getBookId() { return bookId; }
        public void setBookId(int bookId) { this.bookId = bookId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public int getTotalCopies() { return totalCopies; }
        public void setTotalCopies(int totalCopies) { this.totalCopies = totalCopies; }
        public int getAvailableCopies() { return availableCopies; }
        public void setAvailableCopies(int availableCopies) { this.availableCopies = availableCopies; }
    }

    // Get database connection
    private static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(frame, "Database connection error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            throw e;
        }
    }

    // Login authentication
    private static boolean authenticate(String userId, String password) throws SQLException {
        String query = "SELECT is_admin FROM users WHERE user_id = ? AND password = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, userId);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                isAdmin = rs.getBoolean("is_admin");
                loggedInUser = userId;
                return true;
            }
            return false;
        }
    }

    // Add a new book (Create)
    public static void addBook(Book book) throws SQLException {
        String query = "INSERT INTO books (title, author, total_copies, available_copies) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, book.getTitle());
            stmt.setString(2, book.getAuthor());
            stmt.setInt(3, book.getTotalCopies());
            stmt.setInt(4, book.getAvailableCopies());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                book.setBookId(rs.getInt(1));
            }
            JOptionPane.showMessageDialog(frame, "Book added successfully: " + book.getTitle());
        }
    }

    // Update book details (Update)
    public static void updateBook(Book book) throws SQLException {
        String query = "UPDATE books SET title = ?, author = ?, total_copies = ?, available_copies = ? WHERE book_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, book.getTitle());
            stmt.setString(2, book.getAuthor());
            stmt.setInt(3, book.getTotalCopies());
            stmt.setInt(4, book.getAvailableCopies());
            stmt.setInt(5, book.getBookId());
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                JOptionPane.showMessageDialog(frame, "Book updated successfully: " + book.getTitle());
            } else {
                throw new SQLException("Book ID not found: " + book.getBookId());
            }
        }
    }

    // Delete a book (Delete)
    public static void deleteBook(int bookId) throws SQLException {
        String query = "DELETE FROM books WHERE book_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, bookId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                JOptionPane.showMessageDialog(frame, "Book deleted successfully: ID " + bookId);
            } else {
                throw new SQLException("Book ID not found: " + bookId);
            }
        }
    }

    // Issue a book to a user
    public static void issueBook(int bookId, String userId) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            String checkQuery = "SELECT available_copies FROM books WHERE book_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
            checkStmt.setInt(1, bookId);
            ResultSet rs = checkStmt.executeQuery();
            if (!rs.next() || rs.getInt("available_copies") <= 0) {
                throw new SQLException("Book not available or does not exist: ID " + bookId);
            }

            String updateQuery = "UPDATE books SET available_copies = available_copies - 1 WHERE book_id = ?";
            PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
            updateStmt.setInt(1, bookId);
            updateStmt.executeUpdate();

            String issueQuery = "INSERT INTO issued_books (book_id, user_id, issue_date, due_date) VALUES (?, ?, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY))";
            PreparedStatement issueStmt = conn.prepareStatement(issueQuery);
            issueStmt.setInt(1, bookId);
            issueStmt.setString(2, userId);
            issueStmt.executeUpdate();

            conn.commit();
            JOptionPane.showMessageDialog(frame, "Book issued successfully to user: " + userId);
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            JOptionPane.showMessageDialog(frame, "Error issuing book: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            throw e;
        } finally {
            if (conn != null) conn.setAutoCommit(true);
        }
    }

    // Return a book and calculate fines
    public static void returnBook(int bookId, String userId) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            String checkIssueQuery = "SELECT issue_date, due_date FROM issued_books WHERE book_id = ? AND user_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkIssueQuery);
            checkStmt.setInt(1, bookId);
            checkStmt.setString(2, userId);
            ResultSet rs = checkStmt.executeQuery();
            if (!rs.next()) {
                throw new SQLException("No record of book issued to user: " + userId);
            }

            // Calculate fine (1 per day overdue)
            LocalDate dueDate = rs.getDate("due_date").toLocalDate();
            LocalDate today = LocalDate.now();
            long daysOverdue = ChronoUnit.DAYS.between(dueDate, today);
            double fine = daysOverdue > 0 ? daysOverdue * 1.0 : 0.0;

            String deleteIssueQuery = "DELETE FROM issued_books WHERE book_id = ? AND user_id = ?";
            PreparedStatement deleteStmt = conn.prepareStatement(deleteIssueQuery);
            deleteStmt.setInt(1, bookId);
            deleteStmt.setString(2, userId);
            deleteStmt.executeUpdate();

            String updateQuery = "UPDATE books SET available_copies = available_copies + 1 WHERE book_id = ?";
            PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
            updateStmt.setInt(1, bookId);
            updateStmt.executeUpdate();

            conn.commit();
            JOptionPane.showMessageDialog(frame, "Book returned successfully by user: " + userId + "\nFine: $" + fine);
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            JOptionPane.showMessageDialog(frame, "Error returning book: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            throw e;
        } finally {
            if (conn != null) conn.setAutoCommit(true);
        }
    }

    // Search books by title or author
    public static List<Book> searchBooks(String keyword) throws SQLException {
        List<Book> books = new ArrayList<>();
        String query = "SELECT * FROM books WHERE title LIKE ? OR author LIKE ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, "%" + keyword + "%");
            stmt.setString(2, "%" + keyword + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Book book = new Book(rs.getString("title"), rs.getString("author"), rs.getInt("total_copies"));
                book.setBookId(rs.getInt("book_id"));
                book.setAvailableCopies(rs.getInt("available_copies"));
                books.add(book);
            }
        }
        return books;
    }

    // Retrieve all books (Read)
    public static List<Book> getAllBooks() throws SQLException {
        List<Book> books = new ArrayList<>();
        String query = "SELECT * FROM books";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                Book book = new Book(rs.getString("title"), rs.getString("author"), rs.getInt("total_copies"));
                book.setBookId(rs.getInt("book_id"));
                book.setAvailableCopies(rs.getInt("available_copies"));
                books.add(book);
            }
        }
        return books;
    }

    // Update book table in GUI
    private static void updateBookTable(List<Book> books) {
        tableModel.setRowCount(0); // Clear table
        for (Book book : books) {
            tableModel.addRow(new Object[]{book.getBookId(), book.getTitle(), book.getAuthor(), book.getAvailableCopies(), book.getTotalCopies()});
        }
    }

    // Create login panel
    private static JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel userLabel = new JLabel("User ID:");
        userIdField = new JTextField(20);
        JLabel passwordLabel = new JLabel("Password:");
        passwordField = new JPasswordField(20);
        JButton loginButton = new JButton("Login");

        loginButton.addActionListener(e -> {
            try {
                if (authenticate(userIdField.getText(), passwordField.getText())) {
                    frame.getContentPane().removeAll();
                    frame.add(createMainPanel());
                    frame.revalidate();
                    frame.repaint();
                    JOptionPane.showMessageDialog(frame, "Login successful! Welcome, " + loggedInUser);
                } else {
                    JOptionPane.showMessageDialog(frame, "Invalid credentials", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Login error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        panel.add(userLabel);
        panel.add(userIdField);
        panel.add(passwordLabel);
        panel.add(passwordField);
        panel.add(new JLabel());
        panel.add(loginButton);
        return panel;
    }

    // Create main panel with all functionalities
    private static JPanel createMainPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Book management panel (admin only)
        JPanel bookPanel = new JPanel(new GridLayout(5, 2, 10, 10));
        bookPanel.setBorder(BorderFactory.createTitledBorder("Book Management"));

        JLabel titleLabel = new JLabel("Title:");
        titleField = new JTextField(20);
        JLabel authorLabel = new JLabel("Author:");
        authorField = new JTextField(20);
        JLabel copiesLabel = new JLabel("Total Copies:");
        copiesField = new JTextField(20);
        JButton addButton = new JButton("Add Book");
        JButton updateButton = new JButton("Update Book");
        JButton deleteButton = new JButton("Delete Book");

        bookPanel.add(titleLabel);
        bookPanel.add(titleField);
        bookPanel.add(authorLabel);
        bookPanel.add(authorField);
        bookPanel.add(copiesLabel);
        bookPanel.add(copiesField);
        bookPanel.add(addButton);
        bookPanel.add(updateButton);
        bookPanel.add(deleteButton);

        if (!isAdmin) {
            bookPanel.setEnabled(false);
            for (Component c : bookPanel.getComponents()) c.setEnabled(false);
        }

        // Issue/Return panel
        JPanel issuePanel = new JPanel(new GridLayout(5, 2, 10, 10));
        issuePanel.setBorder(BorderFactory.createTitledBorder("Issue/Return Books"));

        JLabel issueBookIdLabel = new JLabel("Book ID:");
        issueBookIdField = new JTextField(20);
        JLabel issueUserIdLabel = new JLabel("User ID:");
        issueUserIdField = new JTextField(20);
        JButton issueButton = new JButton("Issue Book");
        JLabel returnBookIdLabel = new JLabel("Book ID:");
        returnBookIdField = new JTextField(20);
        JLabel returnUserIdLabel = new JLabel("User ID:");
        returnUserIdField = new JTextField(20);
        JButton returnButton = new JButton("Return Book");

        issuePanel.add(issueBookIdLabel);
        issuePanel.add(issueBookIdField);
        issuePanel.add(issueUserIdLabel);
        issuePanel.add(issueUserIdField);
        issuePanel.add(issueButton);
        issuePanel.add(returnBookIdLabel);
        issuePanel.add(returnBookIdField);
        issuePanel.add(returnUserIdLabel);
        issuePanel.add(returnUserIdField);
        issuePanel.add(returnButton);

        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout());
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Books"));
        searchField = new JTextField(20);
        JButton searchButton = new JButton("Search");
        searchPanel.add(new JLabel("Search by Title/Author:"));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);

        // Book table
        String[] columns = {"ID", "Title", "Author", "Available Copies", "Total Copies"};
        tableModel = new DefaultTableModel(columns, 0);
        bookTable = new JTable(tableModel);
        JScrollPane tableScrollPane = new JScrollPane(bookTable);

        // Button actions
        addButton.addActionListener(e -> {
            try {
                String title = titleField.getText();
                String author = authorField.getText();
                int copies = Integer.parseInt(copiesField.getText());
                addBook(new Book(title, author, copies));
                updateBookTable(getAllBooks());
            } catch (SQLException | NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        updateButton.addActionListener(e -> {
            try {
                int bookId = Integer.parseInt(JOptionPane.showInputDialog(frame, "Enter Book ID to Update:"));
                String title = titleField.getText();
                String author = authorField.getText();
                int copies = Integer.parseInt(copiesField.getText());
                Book book = new Book(title, author, copies);
                book.setBookId(bookId);
                book.setAvailableCopies(copies);
                updateBook(book);
                updateBookTable(getAllBooks());
            } catch (SQLException | NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        deleteButton.addActionListener(e -> {
            try {
                int bookId = Integer.parseInt(JOptionPane.showInputDialog(frame, "Enter Book ID to Delete:"));
                deleteBook(bookId);
                updateBookTable(getAllBooks());
            } catch (SQLException | NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        issueButton.addActionListener(e -> {
            try {
                int bookId = Integer.parseInt(issueBookIdField.getText());
                String userId = issueUserIdField.getText();
                issueBook(bookId, userId);
                updateBookTable(getAllBooks());
            } catch (SQLException | NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        returnButton.addActionListener(e -> {
            try {
                int bookId = Integer.parseInt(returnBookIdField.getText());
                String userId = returnUserIdField.getText();
                returnBook(bookId, userId);
                updateBookTable(getAllBooks());
            } catch (SQLException | NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        searchButton.addActionListener(e -> {
            try {
                String keyword = searchField.getText();
                List<Book> books = keyword.isEmpty() ? getAllBooks() : searchBooks(keyword);
                updateBookTable(books);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Layout
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(bookPanel, BorderLayout.NORTH);
        topPanel.add(issuePanel, BorderLayout.CENTER);
        topPanel.add(searchPanel, BorderLayout.SOUTH);
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(tableScrollPane, BorderLayout.CENTER);

        // Initial table update
        try {
            updateBookTable(getAllBooks());
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(frame, "Error loading books: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        return panel;
    }

    // Main method to start the GUI
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Library Management System");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            frame.add(createLoginPanel());
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
