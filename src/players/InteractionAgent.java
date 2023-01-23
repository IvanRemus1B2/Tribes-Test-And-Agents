package players;

import core.actions.Action;
import core.game.GameState;
import utils.ElapsedCpuTimer;

import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;

public class InteractionAgent extends Agent {

    private Random rnd;

    public InteractionAgent( long seed ) {
        super( seed );
        rnd = new Random( seed );
    }

    /**
     * Prints the available actions to the user and returns the action chosen by the user.
     *
     * @param gs  - current game state.
     * @param ect - a timer that indicates when the turn time is due to finish.
     * @return
     */
    @Override
    public Action act( GameState gs , ElapsedCpuTimer ect ) {

        HashMap<Integer, Action> allActions = new HashMap<>();
        int currentAction = 0;

        System.out.println( "\n\nCurrent tick: " + gs.getTick() );
        System.out.println( "\nTribe actions:" );
        var tribeActions = gs.getTribeActions();
        for ( var action : tribeActions ) {
            System.out.println( "Action " + currentAction + ":" + action );
            allActions.put( currentAction++ , action );
        }

        var cityActions = gs.getCityActions();
        System.out.println( "\nCity actions:" );
        for ( var cityId : cityActions.keySet() ) {
            System.out.println( "For city " + cityId );
            for ( var action : cityActions.get( cityId ) ) {
                System.out.println( "Action " + currentAction + ":" + action );
                allActions.put( currentAction++ , action );
            }
        }

        var unitActions = gs.getUnitActions();
        System.out.println( "\nUnit actions:" );
        for ( var unitId : unitActions.keySet() ) {
            System.out.println( "For unit " + unitId );
            for ( var action : unitActions.get( unitId ) ) {
                System.out.println( "Action " + currentAction + ":" + action );
                allActions.put( currentAction++ , action );
            }
        }
        Scanner scanner=new Scanner( System.in );
        int actionIndex;

        do{
            System.out.print("Selected action: ");
            actionIndex=scanner.nextInt();
        }while (allActions.getOrDefault( actionIndex,null )==null);

        return allActions.get( actionIndex );
    }

    @Override
    public Agent copy() {
        return null;
    }
}
