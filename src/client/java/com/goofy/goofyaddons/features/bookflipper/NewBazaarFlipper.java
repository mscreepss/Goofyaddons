package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.Feature;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipCalculator;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipItem;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;
import com.goofy.goofyaddons.utils.ChatUtils;
import com.goofy.goofyaddons.utils.Clock;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public class NewBazaarFlipper implements Feature {
    private enum State{
        START,
        FETCHING,
        STARTUP_CHECK,
        IDLE

    }

    private State state = State.START;
    private State lastState = null;
    private Clock clock = new Clock();
    private FlipCalculator flipCalculator = new FlipCalculator();
    private ScoreboardUtils scoreboardUtils = new ScoreboardUtils();
    private SplittableRandom splittableRandom = new SplittableRandom();
    private boolean running = false;
    private List<FlipItem> flipItemList = new ArrayList<>();
    private boolean notEnoughCash  = false;
    private boolean isStartUpCheckCompleted = false;
    private Minecraft minecraft = Minecraft.getInstance();

    // Tasklist will contain main task meanwhile taskInMemoryList will store duplicate task that will be used later
    private List<Task> taskList = new ArrayList<>();
    private List<Task> taskInMemoryList = new ArrayList<>();


    @Override
    public String name() {
        return "BazaarFlipper";
    }

    @Override
    public void stop() {
        state = State.START;
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
                if (fire("", true)) {
                    minecraft.player.connection.sendCommand("Ec");
                }

            }

        }

    }

    private boolean containerNameCheck(String name) {
        if (minecraft.player == null || minecraft.screen == null) return false;
        return minecraft.screen.getTitle().getString().toLowerCase().contains(name);
    }

    private boolean fire(String name, boolean containerCheck) {
        clock.start(randomizer());
        if (!clock.shouldFire()) return false;
        return containerCheck ? minecraft.screen == null : containerNameCheck(name);
    }


    private void lastStateCheck() {
        if (state == lastState) return;
        ChatUtils.clientMessage("State switched from: " + lastState + " to: " + state);
        clock.stop();
        lastState = state;
        if (state != State.FETCHING) return;
        flipItemList.clear();
        flipCalculator.Refresh();
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

            // If we have any books in the memory list we will just move it out of there
            if (taskInMemoryList.stream().anyMatch(task -> task.getBook().equals(flipItem.book()))) {
                taskInMemoryList.stream().filter(task -> task.getBook().equals(flipItem.book())).findFirst().ifPresent(item -> {
                    taskList.add(item);
                    taskInMemoryList.remove(item);
                });
                continue;
            }
            taskList.add(new Task(flipItem.book(), flipItem.instaBuy(), flipItem.instaSell()));
        }
        state = isStartUpCheckCompleted ? State.IDLE : State.STARTUP_CHECK;
    }

    private int randomizer() {
        int result = splittableRandom.nextInt(GoofyConfig.INSTANCE.minActionDelay, GoofyConfig.INSTANCE.maxActionDelay);
        return result > 50 ? result : 500;
    }
}
