package players.srhea;

import core.actions.Action;
import core.game.GameState;

public interface ActionEvaluatorInterface {
    int getMinimumScore();

    int getMaximumScore();

    int evaluateAction( GameState gameState , Action action );
}
