import core.Types;
import core.actions.Action;
import core.game.Game;
import core.game.GameState;
import org.json.JSONArray;
import players.*;
import gui.GUI;
import gui.WindowInput;
import players.emcts.EMCTSAgent;
import players.emcts.EMCTSParams;
import players.heuristics.PrunePortfolioHeuristic;
import players.mc.MCParams;
import players.mc.MonteCarloAgent;
import players.mcts.MCTSParams;
import players.mcts.MCTSPlayer;
import players.oep.OEPAgent;
import players.oep.OEPParams;
import players.osla.OSLAParams;
import players.osla.OneStepLookAheadAgent;
import players.portfolio.Portfolio;
import players.portfolio.SimplePortfolio;
import players.portfolioMCTS.PortfolioMCTSParams;
import players.portfolioMCTS.PortfolioMCTSPlayer;
import players.rhea.RHEAAgent;
import players.rhea.RHEAParams;
import players.srhea.SRHEAAgent;
import players.srhea.SRHEAParams;

import static core.Constants.*;
import static core.Types.TRIBE.*;
import static core.Types.TRIBE.OUMAJI;

class Run {

    /**
     * Runs 1 game.
     *
     * @param g  - game to run
     * @param ki - Key controller
     * @param ac - Action controller
     */
    static void runGame( Game g , KeyController ki , ActionController ac ) {
        WindowInput wi = null;
        GUI frame = null;
        if (VISUALS) {
            wi = new WindowInput();
            wi.windowClosed = false;
            frame = new GUI( g , "Tribes" , ki , wi , ac , false );
            frame.addWindowListener( wi );
            frame.addKeyListener( ki );
        }

        g.run( frame , wi );
    }


    /**
     * Runs a game, no visuals nor human player
     *
     * @param g - game to run
     */
    static void runGame( Game g ) {
        g.run( null , null );
    }

    static void runGameAsEnvironment( Game game , KeyController ki , ActionController ac ) {
        WindowInput wi = null;
        GUI frame = null;
        if (VISUALS) {
            wi = new WindowInput();
            wi.windowClosed = false;
            frame = new GUI( game , "Tribes" , ki , wi , ac , false );
            frame.addWindowListener( wi );
            frame.addKeyListener( ki );
        }

        game.prepareEnvironment( frame , wi );


        var agents = game.getPlayers();
        int currentPlayer = 0, noPlayers = game.getPlayers().length;
        boolean closedByUser = false;

        while (!game.gameOver()) {

            // The user has closed the frame,thus the game should end
            if (VISUALS && frame != null && frame.isClosed()) {
                closedByUser = true;
                break;
            }

            if (game.playerCanAct( currentPlayer )) {
                Action action;
                var agent = agents[ currentPlayer ];
                Game.ActionStatus actionStatus;

//                if(currentPlayer!=0) {
//                    var grid=game.getGameStateFor( currentPlayer ).getBoard().getTribe( currentPlayer ).getObsGrid();
//                    for(int i=0;i<grid.length;++i){
//                        for(int j=0;j<grid.length;++j){
//                            System.out.print(grid[i][j]+" ");
//                        }
//                        System.out.println();
//                    }
//                }

//                Run.showAllActions( game.getGameStateFor( currentPlayer ) );

                // Request an action from the agent
                action = agent.act( game.getGameStateFor( currentPlayer ) , game.getEct() );

//                System.out.println( "\nTick: " + game.getGameStateFor( game.getCurrentPlayerIndex() ).getTick() );
//                System.out.println( "Current player: " + game.getCurrentPlayerIndex() );
//                System.out.println( "Chosen action: " + action );

                // Keep sending the current action until its acknowledged(includes time limit exceeded) for the case when the game is paused or delayed
                // and the action is not null(in the case of the human agent,where most of the time it gives null actions)
                do {
                    actionStatus = game.act( action , false );
//                    System.out.println(actionStatus);
                } while (actionStatus == Game.ActionStatus.NOT_ACKNOWLEDGED && action != null);

//                System.out.println( "Player " + currentPlayer + " , " + action + " -> " + actionStatus );

                // If the last action made is  END TURN,change to a new player
                if (( action != null && action.getActionType() == Types.ACTION.END_TURN && actionStatus == Game.ActionStatus.ACKNOWLEDGED ) || ( actionStatus == Game.ActionStatus.TIME_LIMIT_EXCEEDED )) {
                    currentPlayer = ( currentPlayer + 1 ) % noPlayers;
                }
            } else {
                // Pass null to specify that this player has finished,and the option to skip the turn
                game.act( null , true );
                currentPlayer = ( currentPlayer + 1 ) % noPlayers;
            }
        }

        if (!closedByUser) {
            // Used to make the game realize it is over
            game.act( null , false );

            // If we use visuals,close only after the player closes
            if (VISUALS) {
                while (frame != null && !frame.isClosed()) {
                    game.act( null , false );
                }
            }
        }
    }

    public static void showAllActions( GameState gameState ) {
        int currentAction = 0;

        System.out.println( "\nActions:" );
        System.out.println( "Tribe actions:" );
        var tribeActions = gameState.getTribeActions();
        for ( var action : tribeActions ) {
            System.out.println( "Action " + currentAction + ":" + action );
        }

        var cityActions = gameState.getCityActions();
        System.out.println( "\nCity actions:" );
        for ( var cityId : cityActions.keySet() ) {
            System.out.println( "For city " + cityId );
            for ( var action : cityActions.get( cityId ) ) {
                System.out.println( "Action " + currentAction + ":" + action );
            }
        }

        var unitActions = gameState.getUnitActions();
        System.out.println( "\nUnit actions:" );
        for ( var unitId : unitActions.keySet() ) {
            System.out.println( "For unit " + unitId );
            for ( var action : unitActions.get( unitId ) ) {
                System.out.println( "Action " + currentAction + ":" + action );
            }
        }
    }

    public enum PlayerType {
        DONOTHING,
        HUMAN,
        RANDOM,
        OSLA,
        MC,
        SIMPLE,
        MCTS,
        RHEA,
        OEP,
        EMCTS,
        PORTFOLIO_MCTS,
        INTERACTION,
        SRHEA
    }

    public static double K_INIT_MULT = 0.5;
    public static double T_MULT = 2.0;
    public static double A_MULT = 1.5;
    public static double B = 1.3;
    public static double[] pMCTSweights;

    public static int MAX_LENGTH;
    public static boolean PRUNING;
    public static boolean PROGBIAS;
    public static boolean FORCE_TURN_END;
    public static boolean MCTS_ROLLOUTS;
    public static int POP_SIZE;


    static Run.PlayerType parsePlayerTypeStr( String arg ) throws Exception {
        switch (arg) {
            case "Human":
                return Run.PlayerType.HUMAN;
            case "Do Nothing":
                return Run.PlayerType.DONOTHING;
            case "Random":
                return Run.PlayerType.RANDOM;
            case "Rule Based":
                return Run.PlayerType.SIMPLE;
            case "OSLA":
                return Run.PlayerType.OSLA;
            case "MC":
                return Run.PlayerType.MC;
            case "MCTS":
                return Run.PlayerType.MCTS;
            case "RHEA":
                return Run.PlayerType.RHEA;
            case "OEP":
                return Run.PlayerType.OEP;
            case "pMCTS":
                return Run.PlayerType.PORTFOLIO_MCTS;
            case "EMCTS":
                return Run.PlayerType.EMCTS;
            case "Interaction":
                return PlayerType.INTERACTION;
            case "SRHEA":
                return PlayerType.SRHEA;
        }
        throw new Exception( "Error: unrecognized Player Type: " + arg );
    }

    static Types.TRIBE parseTribeStr( String arg ) throws Exception {
        switch (arg) {
            case "Xin Xi":
                return XIN_XI;
            case "Imperius":
                return IMPERIUS;
            case "Bardur":
                return BARDUR;
            case "Oumaji":
                return OUMAJI;
            case "Kickoo":
                return KICKOO;
            case "Hoodrick":
                return HOODRICK;
            case "Luxidoor":
                return LUXIDOOR;
            case "Vengir":
                return VENGIR;
            case "Zebasi":
                return ZEBASI;
            case "Ai-Mo":
                return AI_MO;
            case "Quetzali":
                return QUETZALI;
            case "Yadakk":
                return YADAKK;
        }
        throw new Exception( "Error: unrecognized Tribe: " + arg );
    }

    public static double[] getWeights( JSONArray w ) {
        if (w == null) return null;
        double[] weights = new double[ w.length() ];
        for ( int i = 0 ; i < weights.length ; ++i ) {
            weights[ i ] = w.getDouble( i );
        }
        return weights;
    }

    public static Agent getAgent( Run.PlayerType playerType , long agentSeed ) {
        switch (playerType) {
            case DONOTHING:
                return new DoNothingAgent( agentSeed );
            case RANDOM:
                return new RandomAgent( agentSeed );
            case SIMPLE:
                return new SimpleAgentV2( agentSeed );
            case OSLA:
                OSLAParams oslaParams = new OSLAParams();
                oslaParams.stop_type = oslaParams.STOP_FMCALLS; //Upper bound
                oslaParams.heuristic_method = oslaParams.DIFF_HEURISTIC_V1;
                return new OneStepLookAheadAgent( agentSeed , oslaParams );
            case MC:
                MCParams mcparams = new MCParams();
                mcparams.stop_type = mcparams.STOP_FMCALLS;
                mcparams.heuristic_method = mcparams.DIFF_HEURISTIC_V1;
                mcparams.PRIORITIZE_ROOT = true;
                mcparams.ROLLOUT_LENGTH = MAX_LENGTH;
                mcparams.FORCE_TURN_END = FORCE_TURN_END ? 5 : mcparams.ROLLOUT_LENGTH + 1;
                return new MonteCarloAgent( agentSeed , mcparams );
            case MCTS:
                MCTSParams mctsParams = new MCTSParams();
                mctsParams.stop_type = mctsParams.STOP_FMCALLS;
                mctsParams.heuristic_method = mctsParams.DIFF_HEURISTIC_V1;
                mctsParams.PRIORITIZE_ROOT = true;
                mctsParams.ROLLOUT_LENGTH = MAX_LENGTH;
                mctsParams.FORCE_TURN_END = FORCE_TURN_END ? 5 : mctsParams.ROLLOUT_LENGTH + 1;
                mctsParams.ROLOUTS_ENABLED = MCTS_ROLLOUTS;
                return new MCTSPlayer( agentSeed , mctsParams );
            case PORTFOLIO_MCTS:
                PortfolioMCTSParams portfolioMCTSParams = new PortfolioMCTSParams();
                portfolioMCTSParams.stop_type = portfolioMCTSParams.STOP_FMCALLS;
                portfolioMCTSParams.heuristic_method = portfolioMCTSParams.DIFF_HEURISTIC_V1;
                portfolioMCTSParams.PRIORITIZE_ROOT = false;
                portfolioMCTSParams.ROLLOUT_LENGTH = MAX_LENGTH;
                portfolioMCTSParams.PRUNING = PRUNING;
                portfolioMCTSParams.PROGBIAS = PROGBIAS;
                portfolioMCTSParams.K_init_mult = K_INIT_MULT;
                portfolioMCTSParams.A_mult = A_MULT;
                portfolioMCTSParams.B = B;
                portfolioMCTSParams.T_mult = T_MULT;
                Portfolio p = new SimplePortfolio( agentSeed );
                portfolioMCTSParams.setPortfolio( p );
                portfolioMCTSParams.pruneHeuristic = new PrunePortfolioHeuristic( p );
                if (Run.pMCTSweights != null)
                    portfolioMCTSParams.pruneHeuristic.setWeights( Run.pMCTSweights );
                return new PortfolioMCTSPlayer( agentSeed , portfolioMCTSParams );
            case OEP:
                OEPParams oepParams = new OEPParams();
                oepParams.stop_type = oepParams.STOP_FMCALLS;
                oepParams.heuristic_method = oepParams.DIFF_HEURISTIC_V1;
                return new OEPAgent( agentSeed , oepParams );
            case EMCTS:
                EMCTSParams emctsParams = new EMCTSParams();
                emctsParams.stop_type = emctsParams.STOP_FMCALLS;
                emctsParams.heuristic_method = emctsParams.DIFF_HEURISTIC_V1;
                return new EMCTSAgent( agentSeed , emctsParams );
            case RHEA:
                RHEAParams rheaParams = new RHEAParams();
                rheaParams.stop_type = rheaParams.STOP_ITERATIONS;
                rheaParams.heuristic_method = rheaParams.DIFF_HEURISTIC_V1;
                rheaParams.FORCE_TURN_END = rheaParams.INDIVIDUAL_LENGTH + 1;
                return new RHEAAgent( agentSeed , rheaParams );
            case INTERACTION:
                return new InteractionAgent( agentSeed );
            case SRHEA:
                SRHEAParams srheaParams = new SRHEAParams();
                srheaParams.stop_type = srheaParams.STOP_ITERATIONS;
                srheaParams.heuristic_method = srheaParams.DIFF_HEURISTIC_V1;
                srheaParams.FORCE_TURN_END = srheaParams.INDIVIDUAL_LENGTH + 1;
                return new SRHEAAgent( agentSeed , srheaParams );
        }
        return null;
    }
}
