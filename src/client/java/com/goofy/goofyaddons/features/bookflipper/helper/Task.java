package com.goofy.goofyaddons.features.bookflipper.helper;

import java.util.ArrayList;
import java.util.List;

public class Task {
    public enum BookState {
        BAZAAR_ORDER_CHECK,
        SELECTED,
        IN_BUY_ORDER,
        OUTBID,
        STORE,
        ANVIL,
        COMBINE,
        SELL,
        SELL_ORDER,
        REPLACE_SELL
    }

    public boolean instaSell = false;
    public boolean instaBuy = false;
    public boolean selectedThenStoreThenBuyOrder;
    private Book book;
    private int amountToOrder;
    private BookState bookState;
    // book location will be represented in integars, 0 = Inventory, 1 = EnderChest, 2 = EnderChestPage2
    public List<BookList> bookList = new ArrayList<>();


    public Task(Book book, boolean instaBuy, boolean instaSell) {
        this.book = book;
        this.instaBuy = instaBuy;
        this.instaSell = instaSell;
        amountToOrder = book.getQtyAmount(book.level());
    }

    public Book getBook() {
        return book;
    }

    public BookState getBookState() {
        return bookState;
    }

    public void setBookState(BookState bookState) {
        this.bookState = bookState;
    }

    // -1 will indicate failure, 0 will indicate success
    public int assignBook(Book book, int level, int location, int amountOfBook) {
        if (book != this.book) return -1;
        if (book.level() > level) return -1;
        int amount = parseBookLevel(level);
        int totalAmount = amount * amountOfBook;

        if (totalAmount > amountToOrder) return -1;

        amountToOrder -= totalAmount;

        for (int i = 0; i < amountOfBook; i++) {
            bookList.add(new BookList(book, level, location));
        }

        return 0;
    }

    public int getAmountToOrder() {
        return amountToOrder;
    }


    private int parseBookLevel(int level) {
        if (level == book.level()) return 1;
        return 1 << (level - book.level());
    }

}