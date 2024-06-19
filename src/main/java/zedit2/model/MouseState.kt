package zedit2.model

/**
 * Mouse state represents what the pin of the mouse is doing actively. Represents the current button press
 */
enum class MouseState {
        RELEASED,

        /**
         * The mouse is doing an activity:
         */
        PRIMARY,
        SECONDARY,
        MIDDLE
}