package core.game;

import core.Constants;
import core.Types;
import core.actions.Action;
import core.actions.tribeactions.EndTurn;
import core.actors.Tribe;
import players.Agent;
import players.HumanAgent;
import utils.*;
import gui.GUI;
import gui.WindowInput;
import utils.mapelites.Feature;
import utils.stats.AIStats;
import utils.stats.GameplayStats;

import java.util.*;

import static core.Constants.*;
import static core.Types.ACTION.*;

public class Game {

    // State of the game (objects, ticks, etc).
    private GameState gs;

    // GameState objects for players to make decisions
    private GameState[] gameStateObservations;

    // Seed for the game state.
    private long seed;

    //Random number generator for the game.
    private Random rnd;

    // List of players of the game
    private Agent[] players;

    //Number of players of the game.
    private int numPlayers;

    // Is the game paused from the GUI?
    private boolean paused, animationPaused;

    // AI stats for each player.
    private AIStats[] aiStats;

    // Gameplay stats for each player.
    private GameplayStats[] gpStats;

    /**
     * Constructor of the game
     */
    public Game() {
    }

    /**
     * Initializes the game. This method does the following:
     * Sets the players of the game, the number of players and their IDs
     * Initializes the array to hold the player game states.
     * Assigns the tribes that will play the game.
     * Creates the level reading it from the file 'filename'.
     * Resets the game so it's ready to start.
     * Turn order: by default, turns run following the order in the tribes array.
     *
     * @param players  Players of the game.
     * @param filename Name of the file with the level information.
     * @param seed     Seed for the game (used only for board generation)
     * @param gameMode Game Mode for this game.
     */
    public void init( ArrayList<Agent> players , String filename , long seed , Types.GAME_MODE gameMode ) {

        //Initiate the bare bones of the main game classes
        this.seed = seed;
        this.rnd = new Random( seed );
        this.gs = new GameState( rnd , gameMode );

        this.gs.init( filename );
        initGameStructures( players , this.gs.getTribes().length );
        updateAssignedGameStates();
    }

    /**
     * Initializes the game. This method does the following:
     * Sets the players of the game, the number of players and their IDs
     * Initializes the array to hold the player game states.
     * Assigns the tribes that will play the game
     * Generates a new level using the seed levelgen_seed
     * Resets the game so it's ready to start.
     * Turn order: by default, turns run following the order in the tribes array.
     *
     * @param players       Players of the game.
     * @param levelgen_seed Seed for the level generator.
     * @param tribes        Array of tribe types to play with.
     * @param seed          Seed for the game (used only for board generation)
     * @param gameMode      Game Mode for this game.
     */
    public void init( ArrayList<Agent> players , long levelgen_seed , Types.TRIBE[] tribes , long seed , Types.GAME_MODE gameMode ) {

        //Initiate the bare bones of the main game classes
        this.seed = seed;
        this.rnd = new Random( seed );
        this.gs = new GameState( rnd , gameMode );

        this.gs.init( levelgen_seed , tribes );
        initGameStructures( players , tribes );
        updateAssignedGameStates();
    }

    /**
     * Initializes the game from a savegame file
     *
     * @param players  Players who will play this game.
     * @param fileName savegame
     */
    public void init( ArrayList<Agent> players , String fileName ) {

        GameLoader gameLoader = new GameLoader( fileName );
        this.seed = gameLoader.getSeed();
        this.rnd = new Random( seed );
        Tribe[] tribes = gameLoader.getTribes();
        this.gs = new GameState( rnd , gameLoader.getGame_mode() , tribes , gameLoader.getBoard() , gameLoader.getTick() );
        this.gs.setGameIsOver( gameLoader.getGameIsOver() );
        initGameStructures( players , tribes.length );
        updateAssignedGameStates();
    }

    /**
     * Initializes game structures depending on number of players and tribes
     *
     * @param players Players to play this game
     * @param nTribes number of tribes the game is set up to start with. Should be the same as players.size().
     */
    private void initGameStructures( ArrayList<Agent> players , int nTribes ) {
        if (players.size() != nTribes) {
            System.out.println( "ERROR: Number of tribes must _equal_ the number of players. There are " +
                    players.size() + " players for " + nTribes + " tribes in this level." );
            System.exit( -1 );
        }

        //Create the players and agents to control them
        numPlayers = players.size();
        this.players = new Agent[ numPlayers ];
        this.aiStats = new AIStats[ numPlayers ];
        this.gpStats = new GameplayStats[ numPlayers ];

        ArrayList<Integer> allIds = new ArrayList<>();
        for ( int i = 0 ; i < numPlayers ; ++i )
            allIds.add( i );

        for ( int i = 0 ; i < numPlayers ; ++i ) {
            this.players[ i ] = players.get( i );
            this.players[ i ].setPlayerIDs( i , allIds );
            this.aiStats[ i ] = new AIStats( i );
            this.gpStats[ i ] = new GameplayStats( i );
        }

        this.gameStateObservations = new GameState[ numPlayers ];
    }


    /**
     * Initializes game structures depending on number of players and tribes
     *
     * @param players Players to play this game
     * @param tribes  Array of tribe types to play with.
     */
    private void initGameStructures( ArrayList<Agent> players , Types.TRIBE[] tribes ) {
        int nTribes = tribes.length;
        if (players.size() != nTribes) {
            System.out.println( "ERROR: Number of tribes must _equal_ the number of players. There are " +
                    players.size() + " players for " + nTribes + " tribes in this level." );
            System.exit( -1 );
        }

        //Create the players and agents to control them
        numPlayers = players.size();
        this.players = new Agent[ numPlayers ];
        this.aiStats = new AIStats[ numPlayers ];
        this.gpStats = new GameplayStats[ numPlayers ];

        Tribe[] tribeObjects = gs.getTribes();

        for ( int tribeIdx = 0 ; tribeIdx < tribeObjects.length ; ++tribeIdx ) {
            Tribe thisTribe = tribeObjects[ tribeIdx ];
            core.Types.TRIBE tribeType = thisTribe.getType();

            ArrayList<Integer> allIds = new ArrayList<>();
            int indexInTypes = -1;
            for ( int i = 0 ; i < tribes.length ; ++i ) {
                allIds.add( i );
                if (tribes[ i ] == tribeType)
                    indexInTypes = i;
            }

            this.players[ tribeIdx ] = players.get( indexInTypes );
            this.players[ tribeIdx ].setPlayerIDs( tribeIdx , allIds );
            this.aiStats[ tribeIdx ] = new AIStats( tribeIdx );
            this.gpStats[ tribeIdx ] = new GameplayStats( tribeIdx );
        }
        this.gameStateObservations = new GameState[ numPlayers ];
    }

    // Parameters saved to be used in the act method,to allow the game to behave as an environment

    private GUI frame;

    // Turn = a players turn to make actions ending with the END TURN action
    // Tick = each player has done 1 turn in their order
    private boolean firstEnd, newTick, turnEnding;

    // The player that is active in the game at the moment
    private int currentPlayerIndex;

    // Used for time restrictions such as the delay happening at each action,delay at the end turn action
    // and ect for the time limit on a turn
    private ElapsedCpuTimer ect, actionDelayTimer, endTurnDelay;

    // Remaining time
    private long remainingECT;

    // Used for TURN LIMIT to restrict the player such that the next action is END TURN
    private boolean continueTurn;

    // A status of an action when tried to execute it in the game
    public enum ActionStatus {
        ACKNOWLEDGED, NOT_ACKNOWLEDGED, TIME_LIMIT_EXCEEDED
    }

    /**
     * Prepare the environment by initializing the accordingly
     *
     * @param frame the frame
     * @param wi    the window input
     */
    public void prepareEnvironment( GUI frame , WindowInput wi ) {

        if (frame == null || wi == null) {
            VISUALS = false;
        }

        this.frame = frame;

        this.firstEnd = this.newTick = true;
        this.turnEnding = false;

        this.currentPlayerIndex = 0;

        processNewTurn();
    }

    private void processNewTurn() {
        var tribe = gs.getTribes()[ currentPlayerIndex ];
        // Init the turn for this tribe (stars, unit reset, etc).
        gs.initTurn( tribe );

        // Compute the initial player actions and assign the game states.
        gs.computePlayerActions( tribe );
        updateAssignedGameStates();

        // Start the timer to the max duration
        ect = new ElapsedCpuTimer();
        ect.setMaxTimeMillis( TURN_TIME_MILLIS );

        // Keep track of time remaining for turn thinking
        this.remainingECT = TURN_TIME_MILLIS;

        // Timer for action execution, delay introduced from GUI. Another delay is added at the end of the turn to
        // make sure all updates are executed and displayed to humans.
        this.actionDelayTimer = null;
        this.endTurnDelay = null;

        if (VISUALS && frame != null) {
            // If we use visuals,start timer for action delay
            actionDelayTimer = new ElapsedCpuTimer();
            actionDelayTimer.setMaxTimeMillis( FRAME_DELAY );
        }

        this.continueTurn = true;
    }

    /**
     * Check if an action of a given type is found in the available actions presently
     *
     * @param actionType the action type
     * @return true if there exists such an action,false otherwise
     */
    private boolean checkActionTypeAvailable( Types.ACTION actionType ) {
        var allAvailableActions = gs.getAllAvailableActions();
        for ( var action : allAvailableActions ) {
            if (action.getActionType() == actionType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Process an action for the current player.
     * A null value for action means we will not do anything except update the GUI and timers
     *
     * @param action the action
     * @return an action status to say whether the action was possible to execute,not executed or a time limit has been reached
     */
    private ActionStatus processAction( Action action ) {

        // TODO: Clean up the code to not have as many portion of code duplicates
        // TODO: Make it more efficient by trying to minimize the number of NOT_ACKNOWLEDGE gotten
        //  and to assure that TURN_TIME_MILLIS is used properly(after an END TURN ,there are still
        //  some operations and calls for the agent which can create a noticeable delay)

        var playerId = currentPlayerIndex;
        var tribe = gs.getTribes()[ playerId ];

        var actionStatus = Game.ActionStatus.NOT_ACKNOWLEDGED;

        // Take the player for this turn
        Agent ag = players[ playerId ];
        boolean isHumanPlayer = ag instanceof HumanAgent;

        // Check GUI end of turn timer,to determine whether the END TURN action delay is not done
        if (!( endTurnDelay != null && endTurnDelay.remainingTimeMillis() <= 0 )) {
            if (!paused && !animationPaused) {
                // Action request and execution if turn should be continued
                if (continueTurn) {
                    if (( !VISUALS || frame == null ) || actionDelayTimer.remainingTimeMillis() <= 0 || isHumanPlayer) {
                        // If we don't have visuals,a action delay is done or the player is a human agent

                        ect.setMaxTimeMillis( remainingECT );  // Reset timer ignoring all other timers or updates

                        remainingECT = ect.remainingTimeMillis(); // Note down the remaining time to use it for the next iteration

                        if (LOG_STATS && !isHumanPlayer)
                            updateBranchingFactor( aiStats[ playerId ] , gs.getTick() , gameStateObservations[ playerId ] , ag );

                        if (LOG_STATS && action != null)
                            updateGameplayStatsMove( gpStats[ playerId ] , action , gameStateObservations[ playerId ] );

                        if (actionDelayTimer != null) {  // Reset action delay timer for next action request
                            actionDelayTimer = new ElapsedCpuTimer();
                            actionDelayTimer.setMaxTimeMillis( FRAME_DELAY );
                        }

                        // Continue this turn if there are still available actions and end turn was not requested.
                        // If the agent is human, let him play for now.
                        continueTurn = !gs.isTurnEnding();
                        if (!isHumanPlayer) {
                            ect.setMaxTimeMillis( remainingECT );
                            boolean timeOut = TURN_TIME_LIMITED && ect.exceededMaxTime();

                            // Special case:When upgrading a city,there is no END TURN action available in all the actions possible...
                            // However,the framework allows for executing a END TURN either way
                            boolean availableEndAction = checkActionTypeAvailable( END_TURN );
                            if (availableEndAction) {
                                continueTurn &= gs.existAvailableActions( tribe ) && !timeOut;
                            }
                        }

                    } else {
                        // We have visuals,and the delay is still not done so cancel the action
                        action = null;
                    }
                } else if (checkActionTypeAvailable( END_TURN )) {
                    // If turn should be ending (and we've not already triggered end turn), the action is automatically EndTurn
                    action = new EndTurn( gs.getActiveTribeID() );
                }
            } else {
                // Game is paused for animation or request from the user,so switch to null to not execute any action
                action = null;
            }

            // Update GUI after every iteration
            if (VISUALS && frame != null) {
                boolean showAllBoard = Constants.GUI_FORCE_FULL_OBS || Constants.PLAY_WITH_FULL_OBS;

                if (showAllBoard) frame.update( getGameState( -1 ) , action );  // Full Obs
                else frame.update( gameStateObservations[ currentPlayerIndex ] , action );        // Partial Obs

                // Turn should be ending, start timer for delay of next action and show all updates
                if (action != null && action.getActionType() == END_TURN) {
                    if (!isHumanPlayer) {
                        if (endTurnDelay == null) {
                            endTurnDelay = new ElapsedCpuTimer();
                            endTurnDelay.setMaxTimeMillis( FRAME_DELAY );
                        }
                    } else {
                        turnEnding = true;
                        return ActionStatus.ACKNOWLEDGED;
                    }
                }
            } else if (action != null && action.getActionType() == END_TURN) {
                // If no visuals and we should end the turn,do it here
                // Create an END TURN action and finish this players turn accordingly
                gs.next( new EndTurn( playerId ) );
                gs.computePlayerActions( tribe );
                updateAssignedGameStates();
                turnEnding = true;

                return ( TURN_TIME_LIMITED ? ( continueTurn ? ActionStatus.ACKNOWLEDGED : ActionStatus.TIME_LIMIT_EXCEEDED ) : ActionStatus.ACKNOWLEDGED );
            }


            boolean ended = ( action != null && action.getActionType() == END_TURN );

            if (!ended) {
                // We haven't received an END TURN action,continue with this action

//                if (action != null) {
//                    if (!VISUALS || ( frame != null && action.getActionType() != ATTACK )) {
//                        gs.next( action );
//                        gs.computePlayerActions( tribe );
//                        updateAssignedGameStates();
//                        appliedAction = true;
//                    } else if (VISUALS && frame != null && action.getActionType() == ATTACK) {
//                        boolean showAllBoard = Constants.GUI_FORCE_FULL_OBS || Constants.PLAY_WITH_FULL_OBS;
//
//                        if (showAllBoard) frame.update( getGameState( -1 ) , action );  // Full Obs
//                        else frame.update( gameStateObservations[ currentPlayerIndex ] , action );        // Partial Obs
//
//                        gs.next( action );
//                        gs.computePlayerActions( tribe );
//                        updateAssignedGameStates();
//                        appliedAction = true;
//                    }
//                }

                // Action is not null and in the case of the action ATTACK,animation is done,only then update the game state
                if (action != null && !VISUALS || frame != null && ( action != null && !( action.getActionType() == ATTACK ) ||
                        ( action = frame.getAnimatedAction() ) != null )) {
                    // Play the action in the game and update the available actions list and observations
                    // Some actions are animated, the condition above checks if this animation is finished and retrieves
                    // the action after all the GUI updates.
                    gs.next( action );
                    gs.computePlayerActions( tribe );
                    updateAssignedGameStates();
                    actionStatus = ActionStatus.ACKNOWLEDGED;
                }
            }

            if (gameOver()) {
                turnEnding = true;
            }
        } else {
            // Create an END TURN action and finish this players turn accordingly
            gs.next( new EndTurn( playerId ) );
            gs.computePlayerActions( tribe );
            updateAssignedGameStates();
            turnEnding = true;

            actionStatus = ( TURN_TIME_LIMITED ? ( continueTurn ? ActionStatus.ACKNOWLEDGED : ActionStatus.TIME_LIMIT_EXCEEDED ) : ActionStatus.ACKNOWLEDGED );
        }

        return actionStatus;
    }

    /**
     * Process the end of a players turn
     */
    private void processEndTurn() {
        var tribe = gs.getTribes()[ currentPlayerIndex ];
        if (LOG_STATS)
            updateGameplayStatsTurn( gpStats[ currentPlayerIndex ] , gs );

        // Ends the turn for this tribe (units that didn't move heal).
        gs.endTurn( tribe );

        // Save Game
        if (Constants.WRITE_SAVEGAMES)
            GameSaver.writeTurnFile( gs , getBoard() , seed );

        //it may be that this player won the game, no more playing.
        if (gameOver()) {
            newTick = true;
        } else {
            // Check if game should be paused automatically after this turn
            if (VISUALS && frame != null && frame.pauseAfterTurn()) {
                paused = true;
                frame.setPauseAfterTurn( false );
            }

            currentPlayerIndex = ( currentPlayerIndex + 1 ) % players.length;
            if (currentPlayerIndex == 0) {
                // Check if game should be paused automatically after this tick
                if (VISUALS && frame != null && frame.pauseAfterTick()) {
                    paused = true;
                    frame.setPauseAfterTick( false );
                }

                //All turns passed, time to increase the tick.
                gs.incTick();

                newTick = true;
            }
        }
    }

    /**
     * Check if the game is over
     *
     * @return true if the game is over,false otherwise
     */
    private boolean checkGameOver() {
        // Check end of game
        if (firstEnd && gameOver()) {
            terminate();

            firstEnd = false;

            printGameResults();
            if (LOG_STATS) {
                TreeSet<TribeResult> ranking = getCurrentRanking();
                for ( TribeResult tr : ranking ) {
                    int idx = tr.getId();
                    AIStats ais = aiStats[ idx ];
                    if (VERBOSE) ais.print();
                    GameplayStats gps = gpStats[ idx ];
                    gps.logGameEnd( tr );
                    if (VERBOSE) {
                        gps.print();

                        ArrayList<GameplayStats> agps = new ArrayList<>();
                        agps.add( gps );
                        for ( Feature f : Feature.values() ) {
                            double val = f.getFeatureValue( agps );

                            String[] agentChunks = players[ gps.getPlayerID() ].getClass().toString().split( "\\." );
                            String agentName = agentChunks[ agentChunks.length - 1 ];
                            System.out.println( "GPS:" + gps.getPlayerID() + ":" + agentName + ":" + f + ":" + val );
                        }
                    }


                }
            }
            return true;
        }
        return false;
    }

    /**
     * Replaces the run method such that the game updates after each action ,for which the order is set
     * If action is null,this is taken that the current player has no available moves
     *
     * @param action the action to be taken by the current player
     * @return true if the action has taken effect or false otherwise
     * If the game is paused do to animations or the pause buttons,we will not update the
     * game with the action given for the current player.
     */
    public ActionStatus act( Action action , boolean skipPlayersTurn ) {
        boolean gameOver = gameOver();
        ActionStatus actionStatus = ActionStatus.NOT_ACKNOWLEDGED;
        if (newTick) {
            // Check end of game and update
            boolean end = checkGameOver();
            if (end && firstEnd) {
                return ActionStatus.NOT_ACKNOWLEDGED;
            }

            newTick = false;
        }

        if (!gameOver) {
            if (skipPlayersTurn) {
                // Skip the turn of this player if they are finished,indicated by the null action
                if (playerCanAct( currentPlayerIndex )) {
                    System.out.println( "Unexpected problem occurred when giving the action for the environment" );
                    System.exit( 1 );
                }

                // Update whose turn it is
                currentPlayerIndex = ( currentPlayerIndex + 1 ) % players.length;
                if (currentPlayerIndex == 0) {
                    newTick = true;
                }
            } else {
                // Try to execute the action given in the game
                var actionCopy = action != null ? action.copy() : null;
                actionStatus = processAction( actionCopy );

//                var tribe = gs.getTribes()[ currentPlayerIndex ];
//                System.out.println("Move by player "+currentPlayerIndex+" from tribe "+tribe.getType());

                // New turn
                if (turnEnding) {
                    turnEnding = false;
                    processEndTurn();
                    processNewTurn();
                }

                // Ff we got a null action,just update the visuals and don't acknowledge this action
                if (action == null) {
                    return ActionStatus.NOT_ACKNOWLEDGED;
                }
            }
        } else {
            if (frame != null) {
                frame.update( getGameState( -1 ) , null );
            }
            return ActionStatus.ACKNOWLEDGED;
        }
        return actionStatus;
    }

    /**
     * Verify if a player has not lost or won the game
     *
     * @param playerIndex index of the player
     * @return true if the player is still in the game,false otherwise
     */
    public boolean playerCanAct( int playerIndex ) {
        return gs.getTribes()[ playerIndex ].getWinner() == Types.RESULT.INCOMPLETE;
    }

    /**
     * Runs a game once. Receives frame and window input. If any is null, forces a run with no visuals.
     *
     * @param frame window to draw the game
     * @param wi    input for the window.
     */
    public void run( GUI frame , WindowInput wi ) {
        if (frame == null || wi == null)
            VISUALS = false;

        boolean firstEnd = true;

        while (frame == null || !frame.isClosed()) {
//            System.out.println("Frame closed: " + frame.isClosed());
            // Loop while window is still open, even if the game ended.
            // If not playing with visuals, loop is broken when game's ended.

            boolean gameOver = gameOver();
            // Check end of game
            if (firstEnd && gameOver) {
                terminate();

                firstEnd = false;

                printGameResults();
                if (LOG_STATS) {
                    TreeSet<TribeResult> ranking = getCurrentRanking();
                    for ( TribeResult tr : ranking ) {
                        int idx = tr.getId();
                        AIStats ais = aiStats[ idx ];
                        if (VERBOSE) ais.print();
                        GameplayStats gps = gpStats[ idx ];
                        gps.logGameEnd( tr );
                        if (VERBOSE) {
                            gps.print();

                            ArrayList<GameplayStats> agps = new ArrayList<>();
                            agps.add( gps );
                            for ( Feature f : Feature.values() ) {
                                double val = f.getFeatureValue( agps );

                                String[] agentChunks = players[ gps.getPlayerID() ].getClass().toString().split( "\\." );
                                String agentName = agentChunks[ agentChunks.length - 1 ];
//                                System.out.println( "GPS:" + gps.getPlayerID() + ":" + agentName + ":" + f + ":" + val );
                            }
                        }


                    }
                }

                if (!VISUALS || frame == null) {
                    // The game has ended, end the loop if we're running without visuals.
                    break;
                }
            }
            if (!gameOver) {
                tick( frame );
            } else {
                frame.update( getGameState( -1 ) , null );
            }
        }
    }

    /**
     * Ticks the game forward. Asks agents for actions and applies returned actions to obtain the next game state.
     *
     * @param frame GUI of the game
     */
    private void tick( GUI frame ) {

//        System.out.println("Tick: " + gs.getTick());
        Tribe[] tribes = gs.getTribes();
        for ( int i = 0 ; i < numPlayers ; i++ ) {
            Tribe tribe = tribes[ i ];

            if (tribe.getWinner() != Types.RESULT.INCOMPLETE)
                continue; //We don't do anything for tribes that have already finished.


            //play the full turn for this player
            processTurn( i , tribe , frame );

            // Save Game
            if (Constants.WRITE_SAVEGAMES)
                GameSaver.writeTurnFile( gs , getBoard() , seed );

            //it may be that this player won the game, no more playing.
            if (gameOver()) {
                return;
            }

            // Check if game should be paused automatically after this turn
            if (VISUALS && frame != null && frame.pauseAfterTurn()) {
                paused = true;
                frame.setPauseAfterTurn( false );
            }
        }

        // Check if game should be paused automatically after this tick
        if (VISUALS && frame != null && frame.pauseAfterTick()) {
            paused = true;
            frame.setPauseAfterTick( false );
        }

        //All turns passed, time to increase the tick.
        gs.incTick();
    }

    /**
     * Process a turn for a given player. It queries the player for an action until no more
     * actions are available or the player returns a EndTurnAction action.
     *
     * @param playerID ID of the player whose turn is being processed.
     * @param tribe    tribe that corresponds to this player.
     */
    private void processTurn( int playerID , Tribe tribe , GUI frame ) {
        //Init the turn for this tribe (stars, unit reset, etc).
        gs.initTurn( tribe );

        //Compute the initial player actions and assign the game states.
        gs.computePlayerActions( tribe );
        updateAssignedGameStates();

        //Take the player for this turn
        Agent ag = players[ playerID ];
        boolean isHumanPlayer = ag instanceof HumanAgent;

        //start the timer to the max duration
        ElapsedCpuTimer ect = new ElapsedCpuTimer();
        ect.setMaxTimeMillis( TURN_TIME_MILLIS );

        // Keep track of time remaining for turn thinking
        long remainingECT = TURN_TIME_MILLIS;

        boolean continueTurn = true;
        int curActionCounter = 0;

        // Timer for action execution, delay introduced from GUI. Another delay is added at the end of the turn to
        // make sure all updates are executed and displayed to humans.
        ElapsedCpuTimer actionDelayTimer = null;
        ElapsedCpuTimer endTurnDelay = null;
        if (VISUALS && frame != null) {
            actionDelayTimer = new ElapsedCpuTimer();
            actionDelayTimer.setMaxTimeMillis( FRAME_DELAY );
        }

        while (frame == null || !frame.isClosed()) {
            // Keep track of action played in this loop, null if no action.
            Action action = null;

            // Check GUI end of turn timer
            if (endTurnDelay != null && endTurnDelay.remainingTimeMillis() <= 0) break;

            if (!paused && !animationPaused) {
                // Action request and execution if turn should be continued
                if (continueTurn) {
                    //noinspection ConstantConditions
                    if (( !VISUALS || frame == null ) || actionDelayTimer.remainingTimeMillis() <= 0 || isHumanPlayer) {
                        // Get one action from the player
                        ect.setMaxTimeMillis( remainingECT );  // Reset timer ignoring all other timers or updates
                        action = ag.act( gameStateObservations[ playerID ] , ect );
                        remainingECT = ect.remainingTimeMillis(); // Note down the remaining time to use it for the next iteration

                        if (LOG_STATS && !isHumanPlayer)
                            updateBranchingFactor( aiStats[ playerID ] , gs.getTick() , gameStateObservations[ playerID ] , ag );

                        if (LOG_STATS && action != null)
                            updateGameplayStatsMove( gpStats[ playerID ] , action , gameStateObservations[ playerID ] );

                        curActionCounter++;

                        if (actionDelayTimer != null) {  // Reset action delay timer for next action request
                            actionDelayTimer = new ElapsedCpuTimer();
                            actionDelayTimer.setMaxTimeMillis( FRAME_DELAY );
                        }

                        // Continue this turn if there are still available actions and end turn was not requested.
                        // If the agent is human, let him play for now.
                        continueTurn = !gs.isTurnEnding();
                        if (!isHumanPlayer) {
                            ect.setMaxTimeMillis( remainingECT );
                            boolean timeOut = TURN_TIME_LIMITED && ect.exceededMaxTime();
                            continueTurn &= gs.existAvailableActions( tribe ) && !timeOut;
                        }

                    }
                } else if (endTurnDelay == null) {
                    // If turn should be ending (and we've not already triggered end turn), the action is automatically EndTurn
                    action = new EndTurn( gs.getActiveTribeID() );
                }
            }

            // Update GUI after every iteration
            if (VISUALS && frame != null) {
                boolean showAllBoard = Constants.GUI_FORCE_FULL_OBS || Constants.PLAY_WITH_FULL_OBS;

                if (showAllBoard) frame.update( getGameState( -1 ) , action );  // Full Obs
                else frame.update( gameStateObservations[ gs.getActiveTribeID() ] , action );        // Partial Obs

                // Turn should be ending, start timer for delay of next action and show all updates
                if (action != null && action.getActionType() == END_TURN) {
                    if (isHumanPlayer) break;
                    endTurnDelay = new ElapsedCpuTimer();
                    endTurnDelay.setMaxTimeMillis( FRAME_DELAY );
                }

//                try {
//                    Thread.sleep(10);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
            } else if (action != null && action.getActionType() == END_TURN) { // If no visuals and we should end the turn, just break out of loop here
                break;
            }


            if (action != null && !VISUALS || frame != null && ( action != null && !( action.getActionType() == ATTACK ) ||
                    ( action = frame.getAnimatedAction() ) != null )) {
                // Play the action in the game and update the available actions list and observations
                // Some actions are animated, the condition above checks if this animation is finished and retrieves
                // the action after all the GUI updates.
                gs.next( action );
                gs.computePlayerActions( tribe );
                updateAssignedGameStates();
            }

            if (gameOver()) {
                break;
            }
        }

        if (LOG_STATS)
            updateGameplayStatsTurn( gpStats[ playerID ] , gs );

        // Ends the turn for this tribe (units that didn't move heal).
        gs.endTurn( tribe );
    }

    /**
     * Prints the results of the game.
     */
    private void printGameResults() {
        Types.RESULT[] results = getWinnerStatus();
        int[] sc = getScores();
        Tribe[] tribes = gs.getBoard().getTribes();

        TreeSet<TribeResult> ranking = gs.getCurrentRanking();
        System.out.println( gs.getTick() + "; Game Results: " );
        int rank = 1;
        for ( TribeResult tr : ranking ) {
            int tribeId = tr.getId();
            Agent ag = players[ tribeId ];
            String[] agentChunks = ag.getClass().toString().split( "\\." );
            String agentName = agentChunks[ agentChunks.length - 1 ];

            System.out.print( " #" + rank + ": Tribe " + tribes[ tribeId ].getType() + " (" + agentName + "): " + results[ tribeId ] + ",points: " + sc[ tribeId ] );
            System.out.println( " #tech: " + tr.getNumTechsResearched() + ", #cities: " + tr.getNumCities() + ", #production: " + tr.getProduction() );
            System.out.println( "Diplomacy - #wars: " + tribes[ tribeId ].getnWarsDeclared() + ", #stars sent: " + tribes[ tribeId ].getnStarsSent() );
            rank++;
        }
    }


    /**
     * This method call all agents' end-of-game method for post-processing.
     * Agents receive their final game state and reward
     */
    @SuppressWarnings("UnusedReturnValue")
    private void terminate() {

        Tribe[] tribes = gs.getTribes();
        for ( int i = 0 ; i < numPlayers ; i++ ) {
            Agent ag = players[ i ];
            ag.result( gs.copy() , tribes[ i ].getScore() );
        }
    }

    private void updateBranchingFactor( AIStats aiStats , int turn , GameState currentGameState , Agent ag ) {
        ArrayList<Integer> actionCounts = ag.actionsPerUnit( currentGameState );
        aiStats.addBranchingFactor( turn , actionCounts );
        aiStats.addActionsPerStep( turn , ag.actionsPerGameState( gs ) );
    }

    /**
     * Updates the gameplay stats after a move
     */
    private void updateGameplayStatsMove( GameplayStats gps , Action played , GameState curGameState ) {
        gps.logAction( played , curGameState.getTick() );
    }

    /**
     * Updates the gameplay stats at the end of a trun
     */
    private void updateGameplayStatsTurn( GameplayStats gps , GameState curGameState ) {
        gps.logGameState( curGameState );
    }

    /**
     * Returns the winning status of all players.
     *
     * @return the winning status of all players.
     */
    public Types.RESULT[] getWinnerStatus() {
        //Build the results array
        Tribe[] tribes = gs.getTribes();
        Types.RESULT[] results = new Types.RESULT[ numPlayers ];
        for ( int i = 0 ; i < numPlayers ; i++ ) {
            Tribe tribe = tribes[ i ];
            results[ i ] = tribe.getWinner();
        }
        return results;
    }

    /**
     * Returns the current scores of all players.
     *
     * @return the current scores of all players.
     */
    public int[] getScores() {
        //Build the results array
        Tribe[] tribes = gs.getTribes();
        int[] scores = new int[ numPlayers ];
        for ( int i = 0 ; i < numPlayers ; i++ ) {
            scores[ i ] = tribes[ i ].getScore();
        }
        return scores;
    }

    /**
     * Updates the state observations for all players with copies of the
     * current game state, adapted for PO.
     */
    private void updateAssignedGameStates() {

        //TODO: Probably we don't need to do this for all players, just the active one.
        for ( int i = 0 ; i < numPlayers ; i++ ) {
            gameStateObservations[ i ] = getGameState( i );
        }
    }

    /**
     * Returns the game state as seen for the player with the index playerIdx. This game state
     * includes only the observations that are visible if partial observability is enabled.
     *
     * @param playerIdx index of the player for which the game state is generated.
     * @return the game state.
     */
    private GameState getGameState( int playerIdx ) {
        return gs.copy( playerIdx );
    }

    /**
     * Returns the game board.
     *
     * @return the game board.
     */
    public Board getBoard() {
        return gs.getBoard();
    }

    public Agent[] getPlayers() {
        return players;
    }

    /**
     * Method to identify the end of the game. If the game is over, the winner is decided.
     * The winner of a game is determined by TribesConfig.GAME_MODE and TribesConfig.MAX_TURNS
     *
     * @return true if the game has ended, false otherwise.
     */
    public boolean gameOver() {
        return gs.gameOver();
    }

    public void setAnimationPaused( boolean p ) {
        animationPaused = p;
    }

    public void setPaused( boolean p ) {
        paused = p;
    }

    public boolean isPaused() {
        return paused;
    }

    public TreeSet<TribeResult> getCurrentRanking() {
        return gs.getCurrentRanking();
    }

    public GameplayStats getGamePlayStats( int id ) {
        return gpStats[ id ];
    }

    public GameState getGameStateFor( int playerIndex ) {
        return gameStateObservations[ playerIndex ];
    }

    public ElapsedCpuTimer getEct() {
        return ect;
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }
}
