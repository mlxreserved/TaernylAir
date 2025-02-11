import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import BoardingState.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

val bannedPassengers = setOf("Nogartse")

fun main() {
    runBlocking {
        println("Getting the latest flight info...")
        val flights = fetchFlights(listOf("Nogartse"))
        val flightDescriptions = flights.joinToString { "${it.passengerName} (${it.flightNumber})"}
        println("Found flights for $flightDescriptions")
        val flightsAtGate = MutableStateFlow(flights.size)
        launch {
            flightsAtGate
                .takeWhile { it > 0 }
                .onCompletion {
                    println("Finished tracking all flights")
                }
                .collect { flightCount ->
                    println("There are $flightCount flights being tracked")
                }
        }
        launch {
            flights.forEach {
                watchFlight(it)
                flightsAtGate.value = flightsAtGate.value - 1
            }
        }
    }
}

suspend fun watchFlight(initialFlight: FlightStatus) {
    val passengerName = initialFlight.passengerName
    val currentFlight: Flow<FlightStatus> = flow {
        require (passengerName !in bannedPassengers) {
            "Cannot track $passengerName's flight. They are banned from the airport."
        }
        var flight = initialFlight
        while(flight.departureTimeInMinutes >= 0 && !flight.isFlightCanceled) {
            emit(flight)
            delay(1000)
            flight = flight.copy(
                departureTimeInMinutes = flight.departureTimeInMinutes - 1
            )
        }
    }

    currentFlight
        .map { flight ->
            when(flight.boardingStatus) {
                FlightCanceled -> "Your flight was canceled"
                BoardingNotStarted -> "Boarding will start soon"
                WaitingToBoard -> "Other passengers are boarding"
                Boarding -> "You can now board the plane"
                BoardingEnded -> "The boarding doors have closed"
            } + " (Flight departs in ${flight.departureTimeInMinutes} minutes)"
        }
        .onCompletion {
            println("Finished tracking $passengerName's flight")
        }
        .collect { status ->
            println("$passengerName: $status")
        }
}

suspend fun fetchFlights(
    passengerNames: List<String> = listOf("Madrigal", "Polarcubis")
) = passengerNames.map { fetchFlight(it) }