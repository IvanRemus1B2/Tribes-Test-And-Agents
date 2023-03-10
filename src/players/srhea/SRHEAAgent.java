package players.srhea;

import core.actions.Action;
import core.actions.cityactions.CityAction;
import core.actions.unitactions.UnitAction;
import core.actors.Actor;
import core.actors.City;
import core.actors.units.Unit;
import core.game.GameState;
import players.Agent;
import players.heuristics.AlgParams;
import players.heuristics.StateHeuristic;
import utils.ElapsedCpuTimer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SRHEAAgent extends Agent {

    private final Random randomGenerator;
    private StateHeuristic heuristic;
    private final SRHEAParams params;

    private List<Individual> population;
    private int forwardMethodCalls;
    private int currentTurn;
    private boolean newTurn;

    private final ActionEvaluatorInterface actionEvaluator;

    public SRHEAAgent( long seed , SRHEAParams params ) {
        super( seed );

        this.params = params;

        this.randomGenerator = new Random( seed );
        this.population = new ArrayList<>();

        this.newTurn = true;
        this.currentTurn = 0;

        actionEvaluator = new RuleBasedActionEvaluator();
    }

    @Override
    public Action act( GameState gameState , ElapsedCpuTimer ect ) {

        this.heuristic = params.getStateHeuristic( playerID , allPlayerIDs );
        this.forwardMethodCalls = 0;

        if (currentTurn != gameState.getTick()) {
            currentTurn = gameState.getTick();
            newTurn = true;
        }

        if (newTurn) {
            // initialize the population
            initializePopulation( gameState );
        } else {
            // we applied an action,so the first action of each individual needs to be removed
            population = shiftPopulation( gameState );
        }

        startSimulation( gameState );

        newTurn = false;

        // choose the first action from the currently best individual
        return population.get( 0 ).getActions().get( 0 );
    }

    /**
     * Generate the population at random for the given game state
     *
     * @param gameState the given game state
     */
    private void initializePopulation( GameState gameState ) {
        population = new ArrayList<>();

        var randomProbability = params.INIT_RANDOM_PROBABILITY;

        for ( int index = 0 ; index < params.POP_SIZE ; index++ ) {
            population.add( createIndividual( gameState , randomProbability ) );
        }
    }

    /**
     * Create an individual for the given game state using the methods getRandomAction() and getRuleBasedProportionateAction().
     * When choosing an action,with a probability of probabilityRandom we will choose an action
     * given by the getRandomAction() method and 1-probabilityRandom an action from the other function
     *
     * @param gameState         the game state
     * @param probabilityRandom the probability of choosing randomly
     * @return the created individual
     */
    private Individual createIndividual( GameState gameState , double probabilityRandom ) {
        ArrayList<Action> actions = new ArrayList<>();
        var gameStateCopy = gameState.copy();

        while (!gameStateCopy.isGameOver() && actions.size() < params.INDIVIDUAL_LENGTH) {
            double chance = randomGenerator.nextDouble();
            Action action = ( chance <= probabilityRandom ? getRandomAction( gameStateCopy ) : getRuleBasedProportionateAction( gameStateCopy ) );

            advance( gameStateCopy , action );

            actions.add( action );
        }

        Individual ind = new Individual( actions );
        ind.setValue( heuristic.evaluateState( gameState , gameStateCopy ) );

        return ind;
    }

    /**
     * Choose a random action for the current player in the given game state,excluding the actions
     * that are not allowed saved in AlgParams.excludedActions
     *
     * @param gameState the game state
     * @return the action chosen uniformly at random
     */
    private Action getRandomAction( GameState gameState ) {
        if (gameState.isGameOver()) {
            return null;
        }

        var availableActions = this.allGoodActions( gameState , AlgParams.excludedActions );
        return availableActions.get( randomGenerator.nextInt( availableActions.size() ) );
    }

    private Action getRuleBasedProportionateAction( GameState gameState ) {
        var availableActions = this.allGoodActions( gameState , AlgParams.excludedActions );
        var noActions = availableActions.size();

        if (noActions < 1) {
            System.out.println( "The number of actions available should be at least 1" );
            System.exit( 1 );
        }

        // keep the summed scores from the start to an index
        Double[] summedScores = new Double[ noActions ];

        // the summed probabilities from the start to an index
        Double[] probabilities = new Double[ noActions ];

        // the difference with which to add such that the values are at least 1
        double difference = 1 - actionEvaluator.getMinimumScore();

        summedScores[ 0 ] = actionEvaluator.evaluateAction( gameState , availableActions.get( 0 ) ) + difference;
        for ( int index = 1 ; index < noActions ; index++ ) {
            double score = actionEvaluator.evaluateAction( gameState , availableActions.get( index ) ) + difference;
            summedScores[ index ] = score + summedScores[ index - 1 ];
        }

        double totalSum = summedScores[ noActions - 1 ];
        for ( int index = 0 ; index < noActions ; index++ ) {
            probabilities[ index ] = 1.0 * summedScores[ index ] / totalSum;
        }

        // make sure that they add up to 1 at the end
        probabilities[ noActions - 1 ] = 1.0;

        // choose an action at random,proportionate to the score given
        double chance = randomGenerator.nextDouble();

        int chosenAction = 0;
        while (chosenAction < noActions && chance > probabilities[ chosenAction ]) {
            chosenAction++;
        }

        if (chosenAction == noActions) {
            System.out.println( "Problem at the rule based selection,the chosen action index should never reach the value" + noActions );
            System.exit( 1 );
        }

        return availableActions.get( chosenAction );
    }

    /**
     * Advance the game state with the action given.
     *
     * @param gameState the game state
     * @param action    the action which will be performed,assumed to be feasible
     */
    private void advance( GameState gameState , Action action ) {
        gameState.advance( action , true );
        forwardMethodCalls++;
    }

    private ArrayList<Individual> shiftPopulation( GameState gameState ) {
        ArrayList<Individual> newPopulation = new ArrayList<>();

        for ( int index = 0 ; index < params.SHIFT_MOVED_SIZE ; index++ ) {
            shift( gameState , population.get( index ) );
            newPopulation.add( population.get( index ) );
        }

        // Add a number of individuals that are mutated from the best individual currently
        // Exploitation
        for ( int index = params.SHIFT_MOVED_SIZE ; index < params.SHIFT_MOVED_SIZE + params.MUTATE_BEST && index < params.POP_SIZE ; index++ ) {
            Individual ind = mutate( population.get( 0 ) , gameState );
            newPopulation.add( ind );
        }

        // The rest,add random individuals
        // Exploration
        for ( int index = params.SHIFT_MOVED_SIZE + params.MUTATE_BEST ; index < params.POP_SIZE ; index++ ) {
            newPopulation.add( createIndividual( gameState , params.SHIFT_RANDOM_PROBABILITY ) );
        }

        return newPopulation;
    }

    private void shift( GameState gameState , Individual ind ) {

        var gameStateCopy = gameState.copy();
        ind.shift();

        boolean feasible = true;
        int index = 0;
        while (feasible && index < ind.getActions().size()) {
            var action = ind.getActions().get( index );

            feasible = checkActionFeasibility( action , gameStateCopy );

            if (feasible) {
                advance( gameStateCopy , action );
                index++;
            }
        }

        while (!gameStateCopy.isGameOver() && index < params.INDIVIDUAL_LENGTH) {
            var newAction = getRandomAction( gameStateCopy );
            ind.getActions().add( newAction );
            advance( gameStateCopy , newAction );
            index++;
        }

        var score = heuristic.evaluateState( gameState , gameStateCopy );
        ind.setValue( score );
    }

    private Individual mutate( Individual ind , GameState gameState ) {
        ArrayList<Action> chosenActions = new ArrayList<>();
        GameState gameStateCopy = gameState.copy();
        int actionIndex = 0;

        // go through the actions and see if a gene is mutated(action is chosen at random)
        while (!gameStateCopy.isGameOver() && actionIndex < ind.getActions().size()) {
            Action candidate;

            boolean mutateAction = randomGenerator.nextDouble() < params.MUTATION_RATE;
            if (mutateAction) {
                candidate = getRandomAction( gameStateCopy );
            } else {
                // try to keep this action
                candidate = ind.getActions().get( actionIndex );

                boolean isFeasible = checkActionFeasibility( candidate , gameStateCopy );
                if (!isFeasible) {
                    candidate = getRandomAction( gameStateCopy );
                }
            }

            advance( gameStateCopy , candidate );
            chosenActions.add( candidate );
            actionIndex++;
        }

        var newIndividual = new Individual( chosenActions );
        newIndividual.setValue( heuristic.evaluateState( gameState , gameStateCopy ) );
        return newIndividual;
    }

    /**
     * Check the feasibility of an action at the given game state
     *
     * @param action    the action
     * @param gameState the game state
     * @return true if the action is feasible,false otherwise
     */
    private boolean checkActionFeasibility( Action action , GameState gameState ) {
        if (gameState.isGameOver())
            return false;

        if (action instanceof UnitAction ua) {
            int unitId = ua.getUnitId();
            Actor act = gameState.getActor( unitId );
            if (!( act instanceof Unit ) || act.getTribeId() != gameState.getActiveTribeID())
                return false;
        } else if (action instanceof CityAction ca) {
            int cityId = ca.getCityId();
            Actor act = gameState.getActor( cityId );
            if (!( act instanceof City ) || act.getTribeId() != gameState.getActiveTribeID())
                return false;
        }

        boolean feasible = false;
        try {
            feasible = action.isFeasible( gameState );
        } catch (Exception ignored) {
        }

        return feasible;
    }

    private void startSimulation( GameState gameState ) {
        boolean end = false;
        int noIterations = 0;

        while (!end) {
            // Order the population such that the individuals
            // with the highest fitness are at the beginning
            Collections.sort( population );

            // Get the new generation
            population = nextGeneration( gameState );

            noIterations++;
            if (params.stop_type == params.STOP_FMCALLS) {
                end = ( forwardMethodCalls >= params.num_fmcalls );
            } else if (params.stop_type == params.STOP_ITERATIONS) {
                end = ( noIterations >= params.num_iterations );
            }
//            System.out.println("End?"+end);
        }

        // last sort,to ensure we have the order by the best individuals
        Collections.sort( population );
    }

    private ArrayList<Individual> nextGeneration( GameState gameState ) {
        ArrayList<Individual> newPopulation = new ArrayList<>();

        if (params.ELITE_SIZE > 0 && params.POP_SIZE > 1) {
            // Save the elite to the next generation
            for ( int index = 0 ; index < params.ELITE_SIZE && index < params.POP_SIZE ; index++ ) {
                newPopulation.add( population.get( index ) );
            }
        }

        while (newPopulation.size() < params.POP_SIZE) {

            Individual newIndividual = null;

            if (params.POP_SIZE > 1) {
                // create a new individual by doing cross over between 2 parents chosen from a tournament

                int indexParent1 = 0, indexParent2 = 0;
                if (params.selection_type == SRHEAParams.SELECTION_TYPE.TOURNAMENT) {
                    // the first parent
                    indexParent1 = tournamentSelection( new ArrayList<>() );

                    List<Integer> excluded = new ArrayList<>();
                    excluded.add( indexParent1 );

                    // the second parent excluding the first parent
                    indexParent2 = tournamentSelection( excluded );
                } else if (params.selection_type == SRHEAParams.SELECTION_TYPE.ROULETTE_WHEEL) {
                    // the first parent
                    indexParent1 = rouletteWheelSelection( new ArrayList<>() );

                    List<Integer> excluded = new ArrayList<>();
                    excluded.add( indexParent1 );

                    // the second parent excluding the first parent
                    indexParent2 = rouletteWheelSelection( excluded );
                } else {
                    System.out.println( "SRHEA : Unknown selection type" );
                    System.exit( 2 );
                }

                if (params.crossover_type == SRHEAParams.CROSSOVER_TYPE.UNIFORM) {
                    newIndividual = uniformCrossOver( gameState , indexParent1 , indexParent2 );
                } else if (params.crossover_type == SRHEAParams.CROSSOVER_TYPE.COMBINATORIAL) {
                    newIndividual = combinatorialCrossOver( gameState , indexParent1 , indexParent2 );
                } else {
                    System.out.println( "SRHEA : Unknown crossover type" );
                    System.exit( 2 );
                }

                newPopulation.add( newIndividual );
            } else if (params.POP_SIZE == 1) {

                // mutate the only individual and choose the one with a higher value
                newIndividual = mutate( population.get( 0 ) , gameState );
                if (newIndividual.getValue() < population.get( 0 ).getValue()) {
                    newIndividual = population.get( 0 );
                }
            } else {
                System.out.println( "The population size cannot be smaller than 1" );
                System.exit( 1 );
            }

            newPopulation.add( newIndividual );
        }

        return newPopulation;
    }

    /**
     * Select an individual from the population which will be the winner
     * of the tournament of size params.TOURNAMENT_SIZE.In the tournament,only
     * those that are not excluded/forbidden may participate
     *
     * @param excluded the index of the individuals that are excluded
     * @return the index of the chosen individual
     */
    private int tournamentSelection( List<Integer> excluded ) {
        // Chose an individual based on a tournament with the individuals that aren't excluded

        // Generate the list of individuals to consider for the tournament
        List<Integer> candidates = new ArrayList<>();
        for ( int index = 0 ; index < params.POP_SIZE ; ++index ) {
            if (!excluded.contains( index )) {
                candidates.add( index );
            }
        }

        // Shuffle,to make sure we choose randomly from here
        Collections.shuffle( candidates );

        int bestIndex = -1;
        double bestValue = -Double.MAX_VALUE;
        // Choose the best out of the ones which we will select for the tournament
        for ( int index = 0 ; index < params.TOURNAMENT_SIZE ; ++index ) {
            double value = noise( population.get( candidates.get( index ) ).getValue() , params.epsilon , randomGenerator.nextDouble() );
            if (bestValue < value) {
                bestValue = value;
                bestIndex = candidates.get( index );
            }
        }

        return bestIndex;
    }

    /**
     * Select an individual proportionally to his fitness by comparison with the fitness of the others.The
     * excluded individuals will get a fitness of 0,thus will not be selected
     *
     * @param excluded the indexes of the individuals excluded
     * @return the chosen individual
     */
    private int rouletteWheelSelection( List<Integer> excluded ) {
        if (params.POP_SIZE < 1) {
            System.out.println( "The number of individuals should be at least 1" );
            System.exit( 1 );
        }

        // keep the summed scores from the start to an index
        Double[] summedScores = new Double[ params.POP_SIZE ];

        // the summed probabilities from the start to an index
        Double[] probabilities = new Double[ params.POP_SIZE ];

        // the difference with which to add such that the values are at least 1
        double difference = 1;

        // for the indexes that are excluded,just give their score as 0,meaning they will never be chosen proportionally
        summedScores[ 0 ] = ( excluded.contains( 0 ) ? 0 : population.get( 0 ).getValue() + difference );
        for ( int index = 1 ; index < params.POP_SIZE ; index++ ) {
            double score = ( excluded.contains( index ) ? 0 : population.get( index ).getValue() + difference );
            summedScores[ index ] = score + summedScores[ index - 1 ];
        }

        double totalSum = summedScores[ params.POP_SIZE - 1 ];
        for ( int index = 0 ; index < params.POP_SIZE ; index++ ) {
            probabilities[ index ] = 1.0 * summedScores[ index ] / totalSum;
        }

        // make sure that they add up to 1 at the end
        probabilities[ params.POP_SIZE - 1 ] = 1.0;

        // choose an individual at random,proportionate to the score given
        double chance = randomGenerator.nextDouble();

        int chosenIndividual = 0;
        while (chosenIndividual < params.POP_SIZE && chance > probabilities[ chosenIndividual ]) {
            chosenIndividual++;
        }

        if (chosenIndividual == params.POP_SIZE) {
            System.out.println( "Problem at the roulette wheel selection,the chosen action index should never reach the value" + params.POP_SIZE );
            System.exit( 1 );
        }

        return chosenIndividual;
    }

    /**
     * Given a game state and the indexes of 2 parents,create an offspring with the genes
     * chosen uniformly from the parents.If a gene at some position can't be chosen from either parent,
     * choose at random
     *
     * @param gameState    the game state
     * @param indexParent1 the index of the parent 1 from the population
     * @param indexParent2 the index of the parent 1 from the population
     * @return the individual chosen from the parents
     */
    private Individual uniformCrossOver( GameState gameState , int indexParent1 , int indexParent2 ) {
        int actionIndex = 0;
        var gameStateCopy = gameState.copy();
        ArrayList<Action> actions = new ArrayList<>();

        Individual[] parents = { population.get( indexParent1 ) , population.get( indexParent2 ) };

        while (!gameStateCopy.isGameOver() && actionIndex < params.INDIVIDUAL_LENGTH) {
            Action candidate = null;
            boolean isFeasible;

            boolean mutate = randomGenerator.nextDouble() < params.MUTATION_RATE;
            if (mutate) {
                candidate = getRandomAction( gameStateCopy );
                isFeasible = true;
            } else {
                // Don't mutate,choose uniformly from a parent
                int parentIndex = randomGenerator.nextInt( 2 );

                if (actionIndex < parents[ parentIndex ].getActions().size()) {
                    candidate = parents[ parentIndex ].getActions().get( actionIndex );
                    isFeasible = checkActionFeasibility( candidate , gameStateCopy );
                } else {
                    isFeasible = false;
                }

                if (!isFeasible) {
                    // try with the other parent

                    parentIndex = 1 - parentIndex;
                    if (actionIndex < parents[ parentIndex ].getActions().size()) {
                        candidate = parents[ parentIndex ].getActions().get( actionIndex );
                        isFeasible = checkActionFeasibility( candidate , gameStateCopy );
                    }
                }
            }

            if (isFeasible && candidate != null) {
                advance( gameStateCopy , candidate );
                actions.add( candidate );
            }

            actionIndex++;
        }

        // if the number of actions added so far is still smaller than an individual's length,add more actions at random

        while (!gameStateCopy.isGameOver() && actions.size() < params.INDIVIDUAL_LENGTH) {
            Action action = getRandomAction( gameStateCopy );
            advance( gameStateCopy , action );
            actions.add( action );
        }

        Individual newIndividual = new Individual( actions );
        newIndividual.setValue( heuristic.evaluateState( gameState , gameStateCopy ) );
        return newIndividual;
    }

    /**
     * Idea:Focus on trying to place actions that are feasible from both parents to the child,delaying adding random actions
     * as much as possible.Place random actions only after trying to add all the actions from the parents.So the order becomes:
     *
     * @param gameState    the game state
     * @param indexParent1 the index of the parent 1 in the population
     * @param indexParent2 the index of the parent 2 in the population
     * @return the new individual
     */
    private Individual combinatorialCrossOver( GameState gameState , int indexParent1 , int indexParent2 ) {
        ArrayList<Action> actions = new ArrayList<>();

        Individual[] parents = { population.get( indexParent1 ) , population.get( indexParent2 ) };

        int index1, index2;

        index1 = index2 = 0;

        GameState gameStateCopy = gameState.copy();

        // while we haven't got to the end of either parent's actions
        while (!gameStateCopy.isGameOver() && actions.size() < params.INDIVIDUAL_LENGTH && index1 < parents[ 0 ].getActions().size() && index2 < parents[ 1 ].getActions().size()) {
            Action action1, action2;
            boolean feasibleFrom1, feasibleFrom2;

            action1 = parents[ 0 ].getActions().get( index1 );
            feasibleFrom1 = checkActionFeasibility( action1 , gameStateCopy );

            action2 = parents[ 1 ].getActions().get( index2 );
            feasibleFrom2 = checkActionFeasibility( action2 , gameStateCopy );

            if (feasibleFrom1 || feasibleFrom2) {
                //both are feasible,choose one action to add
                int takeFrom = 0;
                if (feasibleFrom1 && feasibleFrom2) {
                    takeFrom = randomGenerator.nextInt( 2 );
                } else if (feasibleFrom2) {
                    takeFrom = 1;
                }

                int index = ( takeFrom == 0 ? index1 : index2 );

                Action candidate = parents[ takeFrom ].getActions().get( index );

                // advance the game state with the candidate chosen
                actions.add( candidate );
                advance( gameStateCopy , candidate );

                if (takeFrom == 0) {
                    index1++;
                } else {
                    index2++;
                }
            } else {
                // no action is feasible,so skip for now
                index1++;
                index2++;
            }
        }

        // while there are still actions in parent 1,add them if they are feasible
        while (!gameStateCopy.isGameOver() && index1 < parents[ 0 ].getActions().size() && actions.size() < params.INDIVIDUAL_LENGTH) {
            Action candidate = parents[ 0 ].getActions().get( index1 );
            boolean isFeasible = checkActionFeasibility( candidate , gameStateCopy );

            if (isFeasible) {
                actions.add( candidate );
                advance( gameStateCopy , candidate );
            }

            index1++;
        }

        // while there are still actions in parent 2,add them if they are feasible
        while (!gameStateCopy.isGameOver() && index2 < parents[ 1 ].getActions().size() && actions.size() < params.INDIVIDUAL_LENGTH) {
            Action candidate = parents[ 1 ].getActions().get( index2 );
            boolean isFeasible = checkActionFeasibility( candidate , gameStateCopy );

            if (isFeasible) {
                actions.add( candidate );
                advance( gameStateCopy , candidate );
            }

            index2++;
        }

        // while the list of actions still has room for more actions,choose at random
        while (!gameStateCopy.isGameOver() && actions.size() < params.INDIVIDUAL_LENGTH) {
            Action action = getRandomAction( gameStateCopy );
            advance( gameStateCopy , action );
            actions.add( action );
        }

        Individual newIndividual = new Individual( actions );
        newIndividual.setValue( heuristic.evaluateState( gameState , gameStateCopy ) );
        return newIndividual;
    }

    private double noise( double input , double epsilon , double random ) {
        return ( input + epsilon ) * ( 1.0 + epsilon * ( random - 0.5 ) );
    }

    @Override
    public Agent copy() {
        return null;
    }
}
