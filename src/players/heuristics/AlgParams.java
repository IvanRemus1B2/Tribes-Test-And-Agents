package players.heuristics;

import core.Types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AlgParams {
    public final int STOP_TIME = 0;
    public final int STOP_ITERATIONS = 1;
    public final int STOP_FMCALLS = 2;

    public final int ENTROPY_HEURISTIC = 0;
    public final int SIMPLE_HEURISTIC = 1;
    public final int DIFF_HEURISTIC_V1 = 2;
    public final int DIFF_HEURISTIC_V2 = 3;
    public int heuristic_method = DIFF_HEURISTIC_V2;

    public double epsilon = 1e-6;

    // Budget settings
    public int stop_type = STOP_TIME;
    public int num_iterations = 5;
    public int num_fmcalls = 2000;
    public int num_time = 40;
    public int FORCE_TURN_END = 5;
    public boolean PRIORITIZE_ROOT = false;

    public static final Types.ACTION[] excludedActions = { Types.ACTION.DISBAND , Types.ACTION.DESTROY , Types.ACTION.SEND_STARS };

    public void setParameterValue( String param , Object value ) {
    }

    public Object getParameterValue( String param ) {
        return null;
    }

    public ArrayList<String> getParameters() {
        return null;
    }

    public Map<String, Object[]> getParameterValues() {
        return null;
    }

    public Map<String, String[]> constantNames() {
        HashMap<String, String[]> names = new HashMap<>();
        names.put( "heuristic_method" , new String[] { "SIMPLE_HEURISTIC" , "ENTROPY_HEURISTIC" } );
        return names;
    }

    public StateHeuristic getStateHeuristic( int playerID , ArrayList<Integer> allIDs ) {
        if (heuristic_method == ENTROPY_HEURISTIC)
            return new TribesEntropyHeuristic( playerID , allIDs );
        else if (heuristic_method == SIMPLE_HEURISTIC) // New method: combined heuristics
            return new TribesSimpleHeuristic( playerID , allIDs );
        else if (heuristic_method == DIFF_HEURISTIC_V1)
            return new TribesDiffHeuristicV1( playerID , allIDs );
        else if (heuristic_method == DIFF_HEURISTIC_V2)
            return new TribesDiffHeuristicV2( playerID , allIDs );
        return null;
    }

    public PruneHeuristic getPruneHeuristic() {
        return null;
    }
}
