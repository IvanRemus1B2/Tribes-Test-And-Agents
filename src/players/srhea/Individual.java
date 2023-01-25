package players.srhea;

import core.actions.Action;

import java.util.ArrayList;

public class Individual implements Comparable<Individual> {

    private final ArrayList<Action> actions;
    private double value;

    public Individual( ArrayList<Action> actions ) {
        this.actions = actions;
        value = 0;
    }

    public void shift() {
        if (actions.size() > 0) {
            actions.remove( 0 );
        }
    }

    @Override
    public int compareTo( Individual other ) {
        if (this == other) {
            return 0;
        }

        return Double.compare( other.getValue() , this.getValue() );

    }

    public ArrayList<Action> getActions() {
        return actions;
    }

    public double getValue() {
        return value;
    }

    public void setValue( double value ) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "Individual{" +
                "actions=" + actions +
                ", value=" + value +
                '}';
    }
}
