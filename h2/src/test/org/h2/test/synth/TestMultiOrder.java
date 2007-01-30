/*
 * Copyright 2004-2006 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.synth;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

public class TestMultiOrder extends TestMultiThread {
    
    Connection conn;
    PreparedStatement insertLine;
    private static final String[] ITEMS = new String[]{"Apples", "Oranges", "Bananas", "Coffee"};

    static int customerCount;
    static int orderCount;
    static int orderLineCount;
    
    TestMultiOrder(TestMulti base) throws SQLException {
        super(base);
        conn = base.getConnection();
    }
    
    void begin() throws SQLException {
        insertLine = conn.prepareStatement("insert into orderLine(order_id, line_id, text, amount) values(?, ?, ?, ?)");
        insertCustomer();
    }
    
    void end() throws SQLException {
        conn.close();
    }

    void operation() throws SQLException {
        if(random.nextInt(10)==0) {
            insertCustomer();
        } else {
            insertOrder();
        }
    }

    private void insertOrder() throws SQLException {
        PreparedStatement prep = conn.prepareStatement("insert into orders(customer_id , total) values(?, ?)");
        prep.setInt(1, random.nextInt(getCustomerCount()));
        BigDecimal total = new BigDecimal("0");
        prep.setBigDecimal(2, total);
        prep.executeUpdate();
        ResultSet rs = prep.getGeneratedKeys();
        rs.next();
        int orderId = rs.getInt(1);
        int lines = random.nextInt(20);
        for(int i=0; i<lines; i++) {
            insertLine.setInt(1, orderId);
            insertLine.setInt(2, i);
            insertLine.setString(3, ITEMS[random.nextInt(ITEMS.length)]);
            BigDecimal amount = new BigDecimal(random.nextInt(100) + "." + random.nextInt(10));
            insertLine.setBigDecimal(4, amount);
            total = total.add(amount);
            insertLine.addBatch();
        }
        insertLine.executeBatch();
        increaseOrderLines(lines);
        prep = conn.prepareStatement("update orders set total = ? where id = ?");
        prep.setBigDecimal(1, total);
        prep.setInt(2, orderId);
        increaseOrders();
        prep.execute();
    }

    private void insertCustomer() throws SQLException {
        PreparedStatement prep = conn.prepareStatement("insert into customer(id, name) values(?, ?)");
        int customerId = getNextCustomerId();
        prep.setInt(1, customerId);
        prep.setString(2, getString(customerId));
        prep.execute();
    }

    private String getString(int id) {
        StringBuffer buff = new StringBuffer();
        Random rnd = new Random(id);
        int len = rnd.nextInt(40);
        for(int i=0; i<len; i++) {
            String s = "bcdfghklmnprstwz";
            char c = s.charAt(rnd.nextInt(s.length()));
            buff.append(i == 0 ? Character.toUpperCase(c) : c);
            s = "aeiou  ";

            buff.append(s.charAt(rnd.nextInt(s.length())));
        }
        return buff.toString();
    }
    
    synchronized int getNextCustomerId() {
        return customerCount++;
    }

    synchronized int increaseOrders() {
        return orderCount++;
    }

    synchronized int increaseOrderLines(int count) {
        return orderLineCount+=count;
    }

    public int getCustomerCount() {
        return customerCount;
    }

    void first() throws SQLException {
        Connection conn = base.getConnection();
        conn.createStatement().execute("drop table customer if exists");
        conn.createStatement().execute("drop table orders if exists");
        conn.createStatement().execute("drop table orderLine if exists");
        conn.createStatement().execute("create table customer(id int primary key, name varchar, account decimal)");
        conn.createStatement().execute("create table orders(id int identity primary key, customer_id int, total decimal)");
        conn.createStatement().execute("create table orderLine(order_id int, line_id int, text varchar, amount decimal, primary key(order_id, line_id))");
        conn.close();
    }

    void finalTest() throws Exception {
        conn = base.getConnection();
        ResultSet rs = conn.createStatement().executeQuery("select count(*) from customer");
        rs.next();
        base.check(rs.getInt(1), customerCount);
        // System.out.println("customers: " + rs.getInt(1));

        rs = conn.createStatement().executeQuery("select count(*) from orders");
        rs.next();
        base.check(rs.getInt(1), orderCount);
        // System.out.println("orders: " + rs.getInt(1));

        rs = conn.createStatement().executeQuery("select count(*) from orderLine");
        rs.next();
        base.check(rs.getInt(1), orderLineCount);
        // System.out.println("orderLines: " + rs.getInt(1));

        conn.close();
    }
}
