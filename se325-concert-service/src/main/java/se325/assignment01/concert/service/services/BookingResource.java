package se325.assignment01.concert.service.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se325.assignment01.concert.common.dto.BookingRequestDTO;
import se325.assignment01.concert.common.dto.UserDTO;

import javax.persistence.EntityManager;
import javax.ws.rs.*;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/concert-service")
public class BookingResource {
    private static Logger LOGGER = LoggerFactory.getLogger(ConcertResource.class);

    @GET
    @Path("bookings/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response GetOthersBookingById(@PathParam("id") Long id) {
        return null;
    }

    @GET
    @Path("/bookings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response GetAllBookingsForUser(@CookieParam("auth") Cookie cookie) {
        LOGGER.info("Get all the bookings for user");
        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {

            return null;
        } finally {
            em.close();
        }
    }
}
