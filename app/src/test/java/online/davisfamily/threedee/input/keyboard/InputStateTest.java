package online.davisfamily.threedee.input.keyboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InputStateTest {

    @Test
    void shouldConsumeSimulationResetRequestOnlyOnce() {
        InputState inputState = new InputState();

        assertFalse(inputState.consumeSimulationResetRequest());

        inputState.requestSimulationReset();

        assertTrue(inputState.consumeSimulationResetRequest());
        assertFalse(inputState.consumeSimulationResetRequest());
    }

    @Test
    void shouldCoalesceSimulationResetRequestsBeforeConsumption() {
        InputState inputState = new InputState();

        inputState.requestSimulationReset();
        inputState.requestSimulationReset();

        assertTrue(inputState.consumeSimulationResetRequest());
        assertFalse(inputState.consumeSimulationResetRequest());
    }
}
