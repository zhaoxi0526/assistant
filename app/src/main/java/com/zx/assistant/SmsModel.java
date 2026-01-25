package com.zx.assistant;

import android.text.format.DateFormat;

public class SmsModel {
    private long id;
    private String address;
    private String body;
    private long date;
    private String person;
    private int read; // 0 for unread, 1 for read

    public SmsModel(long id, String address, String body, long date, String person, int read) {
        this.id = id;
        this.address = address;
        this.body = body;
        this.date = date;
        this.person = person;
        this.read = read;
    }

    // Getters
    public long getId() { return id; }
    public String getAddress() { return address; }
    public String getBody() { return body; }
    public long getDate() { return date; }
    public String getPerson() { return person; }
    public int getRead() { return read; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setAddress(String address) { this.address = address; }
    public void setBody(String body) { this.body = body; }
    public void setDate(long date) { this.date = date; }
    public void setPerson(String person) { this.person = person; }
    public void setRead(int read) { this.read = read; }

    // Format the date to a readable string
    public String getFormattedDate() {
        return android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", date).toString();
    }
}