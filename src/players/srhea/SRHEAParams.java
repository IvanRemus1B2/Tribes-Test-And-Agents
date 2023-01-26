package players.srhea;

import players.heuristics.AlgParams;

public class SRHEAParams extends AlgParams {

    public int POP_SIZE = 100;
    public double MUTATION_RATE = 0.1;
    public int INDIVIDUAL_LENGTH = 20;
    public int TOURNAMENT_SIZE = 10;
    public int MUTATE_BEST = 10;
    public int ELITE_SIZE = 5;

    // When initializing the population,we choose for an individual with this probability
    // whether the action is chosen randomly or proportionally using the evaluator for the score of each action
    // If 1,then all actions are random
    // If 0,then all actions are chosen stochastically and proportionally to the action's score according to the evaluator
    public double INIT_RANDOM_PROBABILITY = 0.5;

    // Similar with the above probability,but for when we shift the population,in case
    // we have to choose an action that is feasible
    public double SHIFT_RANDOM_PROBABILITY = 0.5;

    enum SELECTION_TYPE {
        TOURNAMENT, ROULETTE_WHEEL
    }

    public SELECTION_TYPE selection_type = SELECTION_TYPE.TOURNAMENT;

    enum CROSSOVER_TYPE {
        UNIFORM, COMBINATORIAL
    }

    public CROSSOVER_TYPE crossover_type = CROSSOVER_TYPE.COMBINATORIAL;


    public void print() {
        System.out.println( "SRHEA Params:" );
        System.out.println( "\tPop Size: " + POP_SIZE );
        System.out.println( "\tMutation Rate: " + MUTATION_RATE );
        System.out.println( "\tIndividual Length: " + INDIVIDUAL_LENGTH );
        System.out.println( "\tTournament Size: " + TOURNAMENT_SIZE );
        System.out.println( "\tMutate best: " + MUTATE_BEST );
        System.out.println( "\tElite size: " + ELITE_SIZE );
        System.out.println( "\tThe probability to choose an action at the initialization of the population randomly or proportionally to a score is: " + INIT_RANDOM_PROBABILITY );
    }
}
