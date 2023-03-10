package players;

import core.actions.Action;
import core.game.GameState;
import players.heuristics.AlgParams;
import utils.ElapsedCpuTimer;

import java.util.ArrayList;
import java.util.Random;

public class RandomAgent extends Agent {

    private Random rnd;

    public RandomAgent(long seed)
    {
        super(seed);
        rnd = new Random(seed);
    }

    @Override
    public Action act(GameState gs, ElapsedCpuTimer ect)
    {
//        ArrayList<Action> allActions = gs.getAllAvailableActions();
//        int nActions = allActions.size();
//        Action toExecute = allActions.get(rnd.nextInt(nActions));

        ArrayList<Action> allActions= this.allGoodActions( gs,AlgParams.excludedActions );
        int nActions = allActions.size();

//        System.out.println("[Tribe: " + playerID + "] Tick " +  gs.getTick() + ", num actions: " + nActions + ". Executing " + toExecute);

        return allActions.get(rnd.nextInt(nActions));
    }

    @Override
    public Agent copy() {
        return null;
    }
}
