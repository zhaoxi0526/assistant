package com.zx.assistant;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;

import java.util.ArrayList;
import java.util.List;

public class SmsHelper {
    private Context context;

    public SmsHelper(Context context) {
        this.context = context;
    }

    public List<SmsModel> getAllSms() {
        List<SmsModel> smsList = new ArrayList<>();

        try {
            Uri uri = Telephony.Sms.Inbox.CONTENT_URI;
            String[] projection = {
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.PERSON,
                Telephony.Sms.READ
            };

            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(
                    uri,
                    projection,
                    null,
                    null,
                    Telephony.Sms.DATE + " DESC" // Sort by date descending to get newest first
                );

                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(Telephony.Sms._ID);
                    int addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
                    int bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY);
                    int dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE);
                    int personColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.PERSON);
                    int readColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.READ);

                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);
                        String address = cursor.getString(addressColumn);
                        String body = cursor.getString(bodyColumn);
                        long date = cursor.getLong(dateColumn);
                        String person = cursor.isNull(personColumn) ? null : cursor.getString(personColumn);
                        int read = cursor.getInt(readColumn);

                        smsList.add(new SmsModel(
                            id, address, body, date, person, read
                        ));
                    }
                }
            } catch (SecurityException e) {
                // Handle permission denied
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return smsList;
    }

    public List<SmsModel> getNewSms() {
        Uri uri = Telephony.Sms.Inbox.CONTENT_URI;
        String[] projection = {
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.PERSON,
            Telephony.Sms.READ
        };

        String selection = Telephony.Sms.READ + " = ?";
        String[] selectionArgs = {"0"}; // Only unread messages

        List<SmsModel> smsList = new ArrayList<>();

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                uri,
                projection,
                selection,
                selectionArgs,
                Telephony.Sms.DATE + " DESC"
            );

            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(Telephony.Sms._ID);
                int addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
                int bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY);
                int dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE);
                int personColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.PERSON);
                int readColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.READ);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String address = cursor.getString(addressColumn);
                    String body = cursor.getString(bodyColumn);
                    long date = cursor.getLong(dateColumn);
                    String person = cursor.isNull(personColumn) ? null : cursor.getString(personColumn);
                    int read = cursor.getInt(readColumn);

                    smsList.add(new SmsModel(
                        id, address, body, date, person, read
                    ));
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return smsList;
    }
}