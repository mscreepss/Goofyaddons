package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.Feature;
import com.goofy.goofyaddons.features.bookflipper.helper.*;
import com.goofy.goofyaddons.utils.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;

import java.lang.reflect.Field;
import java.util.*;

public class NewBazaarFlipper implements Feature {
    private enum State{
        START,
        FETCHING,
        STARTUP_CHECK,
        STARTUP_BAZAAR_CHECK,
        IDLE,
        OUTBID,
        BAZAAR_NAVIGATION,
        BUY_ORDER,
        STORE,
        ANVIL,
        COMBINE,
        SELL,
        REPLACE_SELL,
        SELL_ORDER

    }

    private State state = State.START;
    private State lastState = null;
    private Clock clock = new Clock();
    private FlipCalculator flipCalculator = new FlipCalculator();
    private ScoreboardUtils scoreboardUtils = new ScoreboardUtils();
    private SplittableRandom splittableRandom = new SplittableRandom();
    private InventoryScanner inventoryScanner = new InventoryScanner();
    private BazaarMonitor bazaarMonitor = new BazaarMonitor();
    private boolean running = false;
    private List<FlipItem> flipItemList = new ArrayList<>();
    private boolean notEnoughCash  = false;
    private boolean needToStoreExcessBook = false;
    private boolean usingSecondPage = false;
    private boolean isStartUpCheckCompleted = false;
    private Minecraft minecraft = Minecraft.getInstance();
    private boolean checkedFirstPage = false;
    private int store_Counter = 0;
    private boolean attemptedToClaim = false;
    private boolean didReceiveItems = false;
    private Task activeTask = null;
    // 0 will represent inventory, 1 will present first page, 2 will present second page
    private List<BookList> bookLists = new ArrayList<>();
    private HashMap<Integer, Integer> emptyInventorySlots = new HashMap<>();
    private List<Task> taskList = new ArrayList<>();

    private static final Map<Task.BookState, Integer> STATE_PRIORITY = Map.of(
            Task.BookState.BAZAAR_ORDER_CHECK,  1,
            Task.BookState.REPLACE_SELL,        2,
            Task.BookState.ANVIL,               3,
            Task.BookState.STORE,               4,
            Task.BookState.SELECTED,            5,
            Task.BookState.OUTBID,              6
    );


    public NewBazaarFlipper() {
        ChatHook.onMessage("Claimed", this::handleClaimedMessage);
    }

    @Override
    public String name() {
        return "BazaarFlipper";
    }

    @Override
    public void stop() {
        checkedFirstPage = false;
        isStartUpCheckCompleted = false;
        state = State.START;
        taskList.clear();
        running = false;

    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void onTick() {
        if (!running) return;
        lastStateCheck();

        switch (state) {
            case START -> {
                ChatUtils.clientMessage("BazaarFlipper: Started");
                state = State.FETCHING;
            }

            case FETCHING -> {
                flipItemList.addAll(flipCalculator.getFlipItemsList());
                if (flipItemList.isEmpty()) return;
                processData();
            }

            case STARTUP_CHECK -> {
                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand(checkedFirstPage ? GoofyConfig.INSTANCE.secondPage : GoofyConfig.INSTANCE.firstPage);
                }

                if (containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack")) clock.start(randomizer());
                if (containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack") && clock.shouldFire()) {
                    emptyInventorySlots.put(checkedFirstPage ? 2 : 1, inventoryScanner.getEmptyContainerSlots());
                    Set<Integer> counter = new HashSet<>();
                    // in here we check both pages
                    for (Task task : taskList) {
                        List<Integer> slots = inventoryScanner.matchingBookInContainer(task.getBook());
                        if (slots.isEmpty()) continue;
                        for (Integer i : slots) {
                            if (counter.contains(i)) continue;

                            int level = inventoryScanner.getLevel(i);

                            int attempt = task.assignBook(task.getBook(), level, checkedFirstPage ? 2 : 1, 1);
                            System.out.println(attempt);

                            if (attempt == 0) {
                                counter.add(i);
                                continue;
                            }

                            if (!taskList.stream().filter(task1 -> task.getBook().equals(task.getBook())).skip(1).findAny().isPresent()) {
                                handleBookList(task.getBook(), checkedFirstPage ? 2 : 1, level, 1);
                                counter.add(i);
                            }
                        }
                    }

                    if (!checkedFirstPage) {
                        // in here we check inventory
                        for (Task task : taskList) {
                            task.setBookState(Task.BookState.BAZAAR_ORDER_CHECK);
                            List<Integer> slots = inventoryScanner.matchingBookInInventory(task.getBook());
                            if (slots.isEmpty()) continue;
                            for (Integer i : slots) {
                                if (counter.contains(i)) continue;

                                int level = inventoryScanner.getLevel(i);

                                int attempt = task.assignBook(task.getBook(), level, 0, 1);

                                if (attempt == 0) {
                                    counter.add(i);
                                    continue;
                                }

                                if (!taskList.stream().skip(taskList.indexOf(task) + 1).filter(task1 -> task.getBook().equals(task.getBook())).findAny().isPresent()) {
                                    handleBookList(task.getBook(), 0, level, 1);
                                    counter.add(i);
                                }
                            }
                        }

                        emptyInventorySlots.put(0, inventoryScanner.getEmptyContainerSlots());
                        checkedFirstPage = true;
                        minecraft.player.closeContainer();
                        return;
                    }

                    minecraft.player.closeContainer();
                    state = State.IDLE;
                }
            }

            case STARTUP_BAZAAR_CHECK -> {
                Task task = taskInState(Task.BookState.BAZAAR_ORDER_CHECK);
                if (task == null) {
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand("managebazaarorders");
                }

                if (containerNameCheck("Bazaar")) clock.start(randomizer());
                if (containerNameCheck("Bazaar") && clock.shouldFire()) {
                    // Waiting for chat message to appear here
                    if (attemptedToClaim) {
                        if (!didReceiveItems) return;
                        attemptedToClaim = false;
                        didReceiveItems = false;
                    }

                    List<Integer> slot = inventoryScanner.findLoreContainer("BUY " + task.getBook().getRomanLevel(task.getBook().level()));

                    if (slot.isEmpty()) {
                        // first we check if we have all the required books
                        if (task.getAmountToOrder() == 0) {
                            task.setBookState(Task.BookState.ANVIL);
                            return;
                        }

                        // we check if we can combine the books
                        if (task.canCombine) {
                            task.actionSchedule = Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER;
                            task.setBookState(Task.BookState.SELECTED);
                            return;
                        }
                        // if we cannot we check if we have any book in our inventory
                        if (task.bookList.getFirst().location == 0) {
                            task.actionSchedule = Task.ActionSchedule.SELECTED_STORE_BUYORDER;
                            task.setBookState(Task.BookState.SELECTED);
                            return;
                        }
                        activeTask = task;
                        task.setBookState(Task.BookState.SELECTED);
                        return;
                    }

                    int amount = inventoryScanner.checkOrder(slot.getFirst());
                    if (amount > inventoryScanner.getEmptyInventorySlots()) {
                        if (needToStoreExcessBook) {
                            state = State.STORE;
                            return;
                        }

                        Task task1 = taskInState(Task.BookState.ANVIL);
                        if (task1 != null) {
                            state = task1.bookList.getFirst().location == 0 ? State.ANVIL : State.COMBINE;
                            return;
                        }

                        state = State.BAZAAR_NAVIGATION;
                        return;
                    }
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                    handleItemAssigning(task, amount);
                }

                if (containerNameCheck("Order")) clock.start(randomizer());
                if (containerNameCheck("Order") && clock.shouldFire()) {
                    List<Integer> slot = inventoryScanner.findContainer("Cancel Order");
                    if (slot.isEmpty()) return;
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                }

            }

            case IDLE -> {
                if (needToStoreExcessBook) {
                    state = State.STORE;
                    return;
                }

                Task taskToHandle = null;

                // here we loop through every task and pick based of priority
                for (Task task : taskList) {
                    if (!isStartUpCheckCompleted && task.getBookState().equals(Task.BookState.OUTBID) || inventoryScanner.getEmptyInventorySlots() <= 0) continue;
                    Integer rank = STATE_PRIORITY.get(task.getBookState());
                    if (rank == null) continue;

                    if (taskToHandle == null || rank > STATE_PRIORITY.get(taskToHandle.getBookState())) {
                        taskToHandle = task;
                    }
                }

                if (taskToHandle == null) return;
                switch (taskToHandle.getBookState()) {

                    case OUTBID -> state = State.OUTBID;

                    case SELECTED -> {
                        activeTask = taskToHandle;
                        state = State.BAZAAR_NAVIGATION;
                    }

                    case STORE -> state = State.STORE;

                    case ANVIL -> {
                        if (taskToHandle.bookList.getFirst().location != 0) {
                            state = State.COMBINE;
                            return;
                        }
                        state = State.ANVIL;
                    }

                    case REPLACE_SELL -> state = State.REPLACE_SELL;
                }
            }

            case BAZAAR_NAVIGATION -> {

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand(activeTask.getBook().name().replace("Ultimate", ""));
                }

                if (containerNameCheck("Bazaar")) clock.start(randomizer());
                if (containerNameCheck("Bazaar") && clock.shouldFire()) {
                    List<Integer> slots = inventoryScanner.findContainer(activeTask.getBook().getRomanLevel(activeTask.getBook().level()));
                    if (slots.isEmpty()) return;
                    InventoryUtils.clickSlot(slots.getFirst(), false);
                }

                if (containerNameCheck(activeTask.getBook().name())) clock.start(randomizer());
                if (containerNameCheck(activeTask.getBook().name()) && clock.shouldFire()) {
                    InventoryUtils.clickSlot(activeTask.instaBuy ? 10 : 15, false);
                }

                if (containerNameCheck("How many do you want")) clock.start(randomizer());
                if (containerNameCheck("How many do you want") && clock.shouldFire()) {
                    InventoryUtils.clickSlot(16, false);
                }

                if (minecraft.screen instanceof SignEditScreen) clock.start(randomizer());
                if (minecraft.screen instanceof SignEditScreen && clock.shouldFire()) {
                    handleSign();
                }

                if (containerNameCheck("How much do you want to pay")) clock.start(randomizer());
                if (containerNameCheck("How much do you want to pay") && clock.shouldFire()) {
                    bazaarMonitor.add(activeTask.getBook(), inventoryScanner.getUnitPrice(12), false);
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerNameCheck("Confirm")) clock.start(randomizer());
                if (containerNameCheck("Confirm") && clock.shouldFire()) {
                    InventoryUtils.clickSlot(13, false);
                    // first we check if the order was a insta buy
                    if (activeTask.instaBuy) {
                        activeTask.setBookState(activeTask.bookList.getLast().location != 0 ? Task.BookState.ANVIL : Task.BookState.COMBINE);
                        return;
                    }

                    switch (activeTask.actionSchedule) {
                        case SELECTED_COMBINE_STORE_BUYORDER -> {
                            activeTask.setBookState(Task.BookState.COMBINE);
                        }

                        case SELECTED_STORE_BUYORDER -> {
                            activeTask.setBookState(Task.BookState.STORE);
                        }

                        case NONE -> {
                            activeTask.setBookState(Task.BookState.IN_BUY_ORDER);
                        }
                    }
                }
            }

            case STORE -> {
                Task task = taskInState(Task.BookState.STORE);
                if (task == null && !needToStoreExcessBook) {
                    usingSecondPage = false;
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    if (emptyInventorySlots.get(1) == 0) {
                        usingSecondPage = true;
                    }

                    minecraft.player.connection.sendCommand(usingSecondPage ? GoofyConfig.INSTANCE.secondPage : GoofyConfig.INSTANCE.firstPage);
                }

                if (containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack")) clock.start(randomizer());
                if (containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack") && clock.shouldFire()) {
                    BookList bookList = null;
                    if (needToStoreExcessBook) {
                        for (BookList book : bookLists) {
                            if (book.location != 0) break;
                            bookList = book;
                            break;
                        }
                    } else {
                        for (BookList book : task.bookList) {
                            if (book.location != 0) break;
                            bookList = book;
                            break;
                        }
                    }

                    if (bookList == null) {
                        if (needToStoreExcessBook) {
                            needToStoreExcessBook = false;
                            state = isStartUpCheckCompleted ? State.IDLE : State.STARTUP_BAZAAR_CHECK;
                            return;
                        }

                        switch (task.actionSchedule) {
                            case SELECTED_STORE_BUYORDER, SELECTED_COMBINE_STORE_BUYORDER -> {
                                task.setBookState(Task.BookState.IN_BUY_ORDER);
                                task.actionSchedule = Task.ActionSchedule.NONE;
                            }

                        }
                    }

                    List<Integer> slot = inventoryScanner.findLoreInv(bookList.book.getRomanLevel(bookList.level));


                    // compares how many items it had before and how many items it has now to label them as moved or just labeling them once empty
                    if (slot.isEmpty() || store_Counter != 0 && store_Counter > slot.size()) {
                        bookList.location = usingSecondPage ? 2 : 1;
                        return;
                    }

                    store_Counter = slot.size();
                    InventoryUtils.clickSlot(slot.getFirst(), true);
                }
            }

        }

    }

    private boolean containerNameCheck(String name) {
         if (minecraft.screen == null) return false;
        return minecraft.screen.getTitle().toString().contains(name);
    }


    private void lastStateCheck() {
        if (state == lastState) return;
        ChatUtils.clientMessage("State switched from: " + lastState + " to: " + state);
        clock.stop();
        lastState = state;
        store_Counter = 0;
        if (state == State.FETCHING) {
            flipItemList.clear();
            flipCalculator.Refresh();
        }

        if (state == State.STARTUP_BAZAAR_CHECK) {
            emptyInventorySlots.put(0, inventoryScanner.getEmptyInventorySlots());
        }
    }

    private void processData() {
        double purse = scoreboardUtils.getPurse();
        // Money Check
        double cost = flipItemList.stream().mapToDouble(FlipItem::totalCost).min().orElse(-1);

        if (cost != -1) {
            if (cost > purse) {
                notEnoughCash = true;
                state = State.IDLE;
                return;
            }
        }

        for (FlipItem flipItem : flipItemList) {
            if (flipItem.totalCost() > purse) continue;
            if (taskList.stream().anyMatch(task -> task.getBook().equals(flipItem.book()))) continue;
            taskList.add(new Task(flipItem.book(), flipItem.instaBuy(), flipItem.instaSell()));

            Iterator<BookList> iterator = bookLists.iterator();

            while (iterator.hasNext()) {
                BookList bookList = iterator.next();

                if (!bookList.book.equals(flipItem.book())) continue;

                int attempt = taskList.getLast().assignBook(bookList.book, bookList.level, bookList.location, 1);

                if (attempt != -1) iterator.remove();
            }
        }
        state = isStartUpCheckCompleted ? State.IDLE : State.STARTUP_CHECK;
    }

    private int randomizer() {
        int result = splittableRandom.nextInt(GoofyConfig.INSTANCE.minActionDelay, GoofyConfig.INSTANCE.maxActionDelay);
        return result > 50 ? result : 500;
    }

    private Task taskInState(Task.BookState bookState) {
        return taskList.stream().filter(task -> task.getBookState() == bookState).findFirst().orElse(null);
    }

    private void handleBookList(Book book, int location, int level, int amount) {
        if (location == 0) needToStoreExcessBook = true;
        for (int i = 0; i < amount; i++) {
            bookLists.add(new BookList(book, level, location));
        }
        bookLists.sort(Comparator.comparingInt(bookList -> bookList.location));
    }

    private void handleClaimedMessage(String string) {
        if (!didReceiveItems) {
            didReceiveItems = true;
        }
    }

    private void handleItemAssigning(Task task, int amount) {
        if (amount > task.getAmountToOrder()) {
            int newAmount = amount - task.getAmountToOrder();
            task.assignBook(task.getBook(), task.getBook().level(), 0, newAmount);
            handleBookList(task.getBook(), 0, task.getBook().level(), (amount - newAmount));
            task.setBookState(Task.BookState.ANVIL);
            return;
        }
        task.assignBook(task.getBook(), task.getBook().level(), 0, amount);
    }

    private void handleSign() {
        String amountToOrder = String.valueOf(activeTask.getAmountToOrder());
        if (minecraft.screen instanceof AbstractSignEditScreen signScreen) {
            try {
                Field messagesField = AbstractSignEditScreen.class.getDeclaredField("messages");
                messagesField.setAccessible(true);
                String[] messages = (String[]) messagesField.get(signScreen);
                messages[0] = amountToOrder;
                minecraft.setScreen(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
