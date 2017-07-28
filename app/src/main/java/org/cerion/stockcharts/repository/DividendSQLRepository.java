package org.cerion.stockcharts.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.cerion.stockcharts.database.StockDBOpenHelper;
import org.cerion.stockcharts.database.Tables;
import org.cerion.stockcharts.database.Tables.Dividends;
import org.cerion.stocklist.model.Dividend;
import org.cerion.stocklist.model.HistoricalDates;
import org.cerion.stocklist.repository.DividendRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class DividendSQLRepository extends SQLiteRepositoryBase implements DividendRepository {

    public DividendSQLRepository(Context context) {
        super(StockDBOpenHelper.getInstance(context));
    }

    @Override
    public void add(String symbol, List<Dividend> list) {
        SQLiteDatabase db = open();
        db.beginTransaction();

        String table = Dividends.TABLE_NAME;
        //Delete current data before re-adding new
        delete(db, table, String.format("%s='%s'", Dividends._SYMBOL, symbol));

        for(Dividend d : list) {
            ContentValues values = new ContentValues();
            values.put(Dividends._SYMBOL, symbol);
            values.put(Dividends._DATE, d.mDate.getTime());
            values.put(Dividends._AMOUNT, d.mDividend);
            insert(db, table, values);
        }

        addHistory(db, symbol, list);

        db.setTransactionSuccessful();
        db.endTransaction();
        db.close();
    }

    /*
    public List<Dividend> getLatest(String symbol) {
        HistoricalDates dates = getHistoricalDates(symbol);
        boolean update = false;

        if(dates == null) {
            update = true;
        } else {
            Date now = new Date();
            long diff = now.getTime() - dates.LastUpdated.getTime();
            diff /= 1000 * 60 * 60 * 24;
            //Log.d(TAG, symbol + " " + interval.name() + " last updated " + dates.LastUpdated + " (" + diff + " days ago)");

            // TODO based it on the following
            // IF most recent dividend is less than 30 days old, check at most once a week
            // IF no dividends, check once a month, probably wont ever be any
            // If new dividend expected soon, check daily
            if(diff > 7)
                update = true;
        }

        if(update) {
            // TODO API should fail if it doesn't get a valid response, difference between error and success
            List<Dividend> dividends = mYahooFinance.getDividends(symbol);
            add(symbol, dividends); //updatePrices(symbol, interval);
        }

        return get(symbol);
    }
    */

    @Override
    public List<Dividend> get(String symbol) {
        SQLiteDatabase db = openReadOnly();

        String where = String.format(Dividends._SYMBOL + "='%s'", symbol);
        Cursor c = db.query(Dividends.TABLE_NAME, null, where, null, null, null, null);
        List<Dividend> result = new ArrayList<>();

        if(c != null) {
            while (c.moveToNext()) {
                Date date = new Date(c.getLong(c.getColumnIndexOrThrow(Dividends._DATE)));
                float amount = c.getFloat(c.getColumnIndexOrThrow(Dividends._AMOUNT));

                Dividend d = new Dividend(date, amount);
                result.add(d);
            }
            c.close();
        }

        db.close();
        return result;
    }

    @Override
    public HistoricalDates getHistoricalDates(String symbol) {
        SQLiteDatabase db = openReadOnly();

        String where = String.format(StockDBOpenHelper.HistoricalDates._SYMBOL + "='%s'", symbol);
        Cursor c = db.query(StockDBOpenHelper.HistoricalDates.TABLE_HISTORICAL_DATES_DIVIDENDS, null, where, null, null, null, null);
        HistoricalDates result = null;
        if(c != null) {
            if (c.moveToFirst()) {
                result = new HistoricalDates();
                result.Symbol = symbol;
                result.FirstDate = new Date(c.getLong(c.getColumnIndexOrThrow(StockDBOpenHelper.HistoricalDates._FIRST)));
                result.LastDate = new Date(c.getLong(c.getColumnIndexOrThrow(StockDBOpenHelper.HistoricalDates._LAST)));
                result.LastUpdated = new Date(c.getLong(c.getColumnIndexOrThrow(StockDBOpenHelper.HistoricalDates._UPDATED)));
            }
            c.close();
        }

        db.close();
        return result;
    }

    @Override
    public void deleteAll() {
        deleteAll(StockDBOpenHelper.HistoricalDates.TABLE_HISTORICAL_DATES_DIVIDENDS);
        deleteAll(Tables.Dividends.TABLE_NAME);
        optimize();
    }

    private void addHistory(SQLiteDatabase db, String symbol, List<Dividend> list) {

        Collections.sort(list, new Comparator<Dividend>() {
            @Override
            public int compare(Dividend o1, Dividend o2) {
                return o1.mDate.compareTo(o2.mDate);
            }

            @Override
            public boolean equals(Object obj) {
                return false;
            }
        });

        Date first = new Date(0);
        Date last = new Date(0);
        if (list.size() > 0) {
            first = list.get(0).mDate;
            last = list.get(list.size() - 1).mDate;
        }

        ContentValues values = new ContentValues();
        values.put(StockDBOpenHelper.HistoricalDates._SYMBOL, symbol);
        values.put(StockDBOpenHelper.HistoricalDates._FIRST, first.getTime());
        values.put(StockDBOpenHelper.HistoricalDates._LAST, last.getTime());
        values.put(StockDBOpenHelper.HistoricalDates._UPDATED, new Date().getTime());
        db.insertWithOnConflict(StockDBOpenHelper.HistoricalDates.TABLE_HISTORICAL_DATES_DIVIDENDS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
}