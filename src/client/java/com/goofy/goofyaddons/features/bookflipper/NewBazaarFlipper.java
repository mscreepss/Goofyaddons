package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.Feature;
import com.goofy.goofyaddons.features.bookflipper.helper.*;
import com.goofy.goofyaddons.utils.ChatUtils;
import com.goofy.goofyaddons.utils.Clock;
import com.goofy.goofyaddons.utils.InventoryScanner;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import net.minecraft.client.Minecraft;

import java.util.*;

public class NewBazaarFlipper implements Feature {
    private enum State{
        START,
        FETCHING,
        STARTUP_CHECK,
        STARTUP_BAZAAR_CHECK,
        IDLE

    }

    private State state = State.START;
    private State lastState = null;
    private Clock clock = new Clock();
    private FlipCalculator flipCalculator = new FlipCalculator();
    private ScoreboardUtils scoreboardUtils = new ScoreboardUtils();
    private SplittableRandom splittableRandom = new SplittableRandom();
    private InventoryScanner inventoryScanner = new InventoryScanner();
    private boolean running = false;
    private List<FlipItem> flipItemList = new ArrayList<>();
    private boolean notEnoughCash  = false;
    private boolean needToStoreExcessBook = false;
    private boolean isStartUpCheckCompleted = false;
    private Minecraft minecraft = Minecraft.getInstance();
    private boolean checkedFirstPage = false;
    // 0 will represent inventory, 1 will present first page, 2 will present second page
    private List<BookList> bookLists = new ArrayList<>();
    private HashMap<Integer, Integer> emptyInventorySlots = new HashMap<>();
    private List<Task> taskList = new ArrayList<>();


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
                                    task.selectedThenStoreThenBuyOrder = true;
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

                if (fire("", true)) {
                    minecraft.player.connection.sendCommand("managebazaarorders");
                }

                if (fire("bazaar", false)) {
                    List<Integer> slot = inventoryScanner.findLoreContainer("BUY " + task.getBook().getRomanLevel(task.getBook().level()));

                    if (slot.isEmpty()) {

                    }
                }

            }

            case IDLE -> {
            }

        }

    }

    private boolean containerNameCheck(String name) {
         if (minecraft.screen == null) return false;
        return minecraft.screen.getTitle().toString().contains(name);
    }

    private boolean fire(String name, boolean containerCheck) {
        if (containerCheck && minecraft.screen == null) {
            clock.start(randomizer());
            return clock.shouldFire();
        }
        if (containerNameCheck(name)) {
            clock.start(randomizer());
            return clock.shouldFire();
        }
        return false;
    }


    private void lastStateCheck() {
        if (state == lastState) return;
        ChatUtils.clientMessage("State switched from: " + lastState + " to: " + state);
        clock.stop();
        lastState = state;
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
    }
}
