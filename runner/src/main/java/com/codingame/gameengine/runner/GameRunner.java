package com.codingame.gameengine.runner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.codingame.gameengine.runner.Command.InputCommand;
import com.codingame.gameengine.runner.Command.OutputCommand;
import com.codingame.gameengine.runner.dto.GameResult;
import com.codingame.gameengine.runner.dto.Tooltip;
import com.google.gson.Gson;

public class GameRunner {

    public static final String INTERRUPT_THREAD = "05&08#1981";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Pattern COMMAND_HEADER_PATTERN = Pattern
            .compile("\\[\\[(?<cmd>.+)\\] ?(?<lineCount>[0-9]+)\\]");

    protected static Log log = LogFactory.getLog(GameRunner.class);
    GameResult gameResult = new GameResult();

    private Agent referee;
    private final List<Agent> players;
    private final List<AsynchronousWriter> writers = new ArrayList<>();
    private final List<BlockingQueue<String>> queues = new ArrayList<>();
    private int lastPlayerId = 0;

    private static enum OutputResult {
        OK, TIMEOUT, TOOLONG, TOOSHORT
    };

    public GameRunner() {
        this(null);
    }

    public GameRunner(String initFile) {
        try {
            referee = new RefereeAgent();
            players = new ArrayList<Agent>();

            if (initFile != null) {
                gameResult.refereeInput = FileUtils.readFileToString(new File(initFile), UTF8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot initialize game", e);
        }
    }

    public void initialize(Properties conf) {
        if (players.size() == 0) throw new RuntimeException("You have to add at least one player");

        referee.initialize(conf);
        gameResult.outputs.put("referee", new ArrayList<>());
        gameResult.errors.put("referee", new ArrayList<>());

        for (int i = 0; i < players.size(); i++) {
            String id = String.valueOf(i);
            Agent player = players.get(i);
            player.initialize(conf);

            List<String> initOutputsValues = new ArrayList<>();
            initOutputsValues.add(null);
            gameResult.outputs.put(id, initOutputsValues);

            List<String> initErrorsValues = new ArrayList<>();
            gameResult.errors.put(id, initErrorsValues);
        }
    }

    private void bootstrapPlayers() {
        boolean allFailed = true;
        for (int i = 0; i < players.size(); i++) {
            Agent player = players.get(i);
            player.execute();
            allFailed = allFailed && player.isFailed();
        }

        if (allFailed) {
            throw new RuntimeException("Bootstrap of all players failed to bootsrap");
        }

        try {
            Thread.sleep(300); // Arbitrary time to wait for bootstrap
        } catch (InterruptedException e) {
        }

        for (Agent agent : players) {
            BlockingQueue<String> queue = new ArrayBlockingQueue<>(1024);
            AsynchronousWriter asyncWriter = new AsynchronousWriter(queue, agent.getInputStream());
            writers.add(asyncWriter);
            queues.add(queue);
            asyncWriter.start();
        }
    }

    public void run() {
        referee.execute();

        bootstrapPlayers();

        readInitFrameErrors();

        Command initCommand = new Command(OutputCommand.INIT);
        initCommand.addLine(players.size());

        // If the referee has input data (i.e. value for seed)
        if (gameResult.refereeInput != null) {
            try (Scanner scanner = new Scanner(gameResult.refereeInput)) {
                while (scanner.hasNextLine()) {
                    initCommand.addLine((scanner.nextLine()));
                }
            }
        }

        referee.sendInput(initCommand.toString());
        int round = 0;
        while (true) {
            GameTurnInfo turnInfo = readGameInfo();
            boolean validTurn = turnInfo.isComplete();

            if (validTurn) {
                gameResult.outputs.get("referee").add(turnInfo.get(InputCommand.INFOS).orElse(null));
                gameResult.summaries.add(turnInfo.get(InputCommand.SUMMARY).orElse(null));
            }

            if ((validTurn) && (!turnInfo.get(InputCommand.SCORES).isPresent())) {
                NextPlayerInfo nextPlayerInfo = new NextPlayerInfo(turnInfo.get(InputCommand.NEXT_PLAYER_INFO).orElse(null));
                String nextPlayerOutput = getNextPlayerOutput(nextPlayerInfo, turnInfo.get(InputCommand.NEXT_PLAYER_INPUT).orElse(null));
                if (nextPlayerOutput != null) {
                    sendPlayerOutput(nextPlayerOutput, nextPlayerInfo.nbLinesNextOutput);
                } else {
                    sendTimeOut();
                }
            }

            readError(referee);
            if (!validTurn) {
                gameResult.views.add(null);
            } else {
                gameResult.views.add(turnInfo.get(InputCommand.VIEW).orElse(null));

                turnInfo.get(InputCommand.UINPUT).ifPresent(line -> {
                    gameResult.uinput.add(line);
                });

                turnInfo.get(InputCommand.METADATA).ifPresent(line -> {
                    gameResult.metadata = line;
                });

                final int currentRound = round;
                turnInfo.get(InputCommand.TOOLTIP).ifPresent(line -> {
                    String[] tooltipData = line.split("\n");
                for (int i = 0; i < tooltipData.length / 2; ++i) {
                    String text = tooltipData[i * 2];
                    int eventId = Integer.valueOf(tooltipData[i * 2 + 1]);
                        gameResult.tooltips.add(new Tooltip(text, eventId, currentRound));
                }
                });

                turnInfo.get(InputCommand.SCORES).ifPresent(scores -> {
                for (String line : scores.split("\n")) {
                    String[] parts = line.split(" ");
                    if (parts.length > 1) {
                        int player = Integer.decode(parts[0]);
                        int score = Integer.decode(parts[1]);
                        gameResult.scores.put(player, score);
                    }
                }
                });
            }
            round++;
            if (!validTurn || turnInfo.isEndTurn()) {
                break;
            }
        }

        for (BlockingQueue<String> queue : queues) {
            queue.offer(INTERRUPT_THREAD);
        }

    }

    private String getJSONResult() {
        for (int i = 0; i < players.size(); i++) {
            gameResult.ids.put(i, players.get(i).getAgentId());
        }
        return new Gson().toJson(gameResult);
    }

    /**
     * Read all output from standard error stream
     */
    private void readInitFrameErrors() {
        gameResult.errors.get("referee").add(referee.readError());
        for (int i = 0; i < players.size(); i++) {
            Agent player = players.get(i);
            String id = String.valueOf(i);
            gameResult.errors.get(id).add(player.readError());
        }
    }

    /**
     * Read all output from standard error stream
     */
    private void readError(Agent agent) {
        if (agent == referee) {
            gameResult.errors.get("referee").add(referee.readError());
        } else {
            for (int i = 0; i < players.size(); i++) {
                if (players.get(i) == agent) {
                    gameResult.errors.get(String.valueOf(i)).add(agent.readError());
                    break;
                }
            }
        }
    }

    private void sendPlayerOutput(String output, int nbLines) {
        Command command = new Command(OutputCommand.SET_PLAYER_OUTPUT, output.split("(\\n|\\r\\n)"));
        referee.sendInput(command.toString());
    }

    private void sendTimeOut() {
        Command command = new Command(OutputCommand.SET_PLAYER_TIMEOUT);
        referee.sendInput(command.toString());
    }

    private String getNextPlayerOutput(NextPlayerInfo nextPlayerInfo, String nextPlayerInput) {
        Agent player = players.get(nextPlayerInfo.nextPlayer);

        // Send player input to input queue
        queues.get(nextPlayerInfo.nextPlayer).offer(nextPlayerInput);

        // Wait for player output then read error
        String playerOutput = player.getOutput(nextPlayerInfo.nbLinesNextOutput, nextPlayerInfo.timeout);
        if (playerOutput != null)
            playerOutput = playerOutput.replace('\r', '\n');
        readError(player);

        gameResult.outputs.get(String.valueOf(nextPlayerInfo.nextPlayer)).add(playerOutput);

        if (checkOutput(playerOutput, nextPlayerInfo.nbLinesNextOutput) != OutputResult.OK)
            return null;
        if ((playerOutput != null) && playerOutput.isEmpty() && (nextPlayerInfo.nbLinesNextOutput == 1))
            return "\n";
        if ((playerOutput != null) && (playerOutput.length() > 0)
                && (playerOutput.charAt(playerOutput.length() - 1) != '\n'))
            return playerOutput + '\n';

        return playerOutput;
    }

    private GameTurnInfo readGameInfo() {
        GameTurnInfo turnInfo = new GameTurnInfo();

        referee.sendInput(new Command(OutputCommand.GET_GAME_INFO).toString());

        while (!turnInfo.isComplete()) {
            Command command = readCommand(referee);
            if (command == null)
                return turnInfo;
            turnInfo.put(command);
        }
        return turnInfo;
    }

    private Command readCommand(Agent agent) {
        String output = "";
        output = agent.getOutput(1, 1500);
        if (output != null)
            output = output.replace('\r', '\n');
        if (checkOutput(output, 1) != OutputResult.OK)
            return null;

        Matcher m = COMMAND_HEADER_PATTERN.matcher(output.trim());
        if (m.matches()) {
            String command = m.group("cmd");
            int nbLinesToRead = Integer.parseInt(m.group("lineCount"));

            if (nbLinesToRead >= 0) {
                output = agent.getOutput(nbLinesToRead, 500);
            } else {
                output = null;
            }
            if (checkOutput(output, nbLinesToRead) != OutputResult.OK)
                return null;
            return new Command(InputCommand.valueOf(command), output);
        } else {
            throw new RuntimeException("Invalid referee command: " + output);
        }
    }

    private OutputResult checkOutput(String output, int nbExpectedLines) {
        if ((output == null) || (output.isEmpty())) {
            if (nbExpectedLines <= 0) {
                return OutputResult.OK;
            } else {
                return OutputResult.TIMEOUT;
            }
        }

        int nbOccurences = 0;
        for (int i = 0; i < output.length(); ++i) {
            if (output.charAt(i) == '\n') {
                ++nbOccurences;
            }
        }

        if (nbOccurences < nbExpectedLines)
            return OutputResult.TOOSHORT;
        if (nbOccurences > nbExpectedLines)
            return OutputResult.TOOLONG;
        return OutputResult.OK;
    }

    private void addPlayer(Agent player) {
        player.setAgentId(lastPlayerId++);
        players.add(player);
    }

    public void addJavaPlayer(Class<?> playerClass) {
        addPlayer(new JavaPlayerAgent(playerClass.getName()));
    }

    public void addCommandLinePlayer(String commandLine) {
        addPlayer(new CommandLinePlayerAgent(commandLine));
    }

    public void start() {
        start(8888);
    }

    public void start(int port) {
        Properties conf = new Properties();
        initialize(conf);
        run();

        new Renderer(port).render(players.size(), getJSONResult());
    }

    static class NextPlayerInfo {

        int nextPlayer;
        int nbLinesNextOutput;
        long timeout;

        NextPlayerInfo(String command) {
            String[] nextPlayerInfo = command.split("\n");
            nextPlayer = Integer.decode(nextPlayerInfo[0]);
            nbLinesNextOutput = Integer.decode(nextPlayerInfo[1]);
            timeout = Long.decode(nextPlayerInfo[2]);
        }
    }
}