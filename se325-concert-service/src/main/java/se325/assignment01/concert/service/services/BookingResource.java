package se325.assignment01.concert.service.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se325.assignment01.concert.common.dto.BookingDTO;
import se325.assignment01.concert.common.dto.BookingRequestDTO;
import se325.assignment01.concert.service.domain.Booking;
import se325.assignment01.concert.service.domain.Concert;
import se325.assignment01.concert.service.domain.Seat;
import se325.assignment01.concert.service.domain.User;
import se325.assignment01.concert.service.mapper.BookingMapper;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.URI;
import java.util.List;


@Path("/concert-service")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookingResource {
    private static Logger LOGGER = LoggerFactory.getLogger(ConcertResource.class);
    public static final String AUTH_COOKIE = "auth";

    @POST
    @Path("/bookings")
    public Response makeBooking(BookingRequestDTO dto, @CookieParam(AUTH_COOKIE) Cookie cookie) {
        LOGGER.info("Try to make a booing of concert" + dto.getConcertId() + "on Date" + dto.getDate());

        if (cookie == null) { //Haven't login
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Booking newBooking;
        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();
            TypedQuery<User> userQuery = em.createQuery("select u from User u where u.cookie = :cookie", User.class)
                    .setParameter("cookie", cookie.getValue());
            User user = userQuery.getResultList().stream().findFirst().orElse(null); //gets a single user

            if (user == null) { //Havn't sign up
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            LOGGER.info("Login successful");

            Concert concert = em.find(Concert.class, dto.getConcertId());

            if (concert == null) { // No concert
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            if (!concert.getDates().contains(dto.getDate())) { //The date is wrong
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            // one query to get all requested seats
            List<Seat> seats = em.createQuery("select s from Seat s where s.date = :dates and s.isBooked = false and s.label in :seats", Seat.class)
                    .setParameter("seats", dto.getSeatLabels())
                    .setParameter("dates", dto.getDate())
                    .getResultList();

            //At least one seat is already booked
            if (seats.size() != dto.getSeatLabels().size()) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }

            for (Seat seat : seats) { //Book all seats
                seat.setBooked(true);
            }

            newBooking = new Booking(dto.getConcertId(), dto.getDate(), seats, user);
            em.persist(newBooking);
            em.getTransaction().commit();

        } finally {
            em.close();
        }

        return Response.created(URI.create("concert-service/bookings/"+ newBooking.getId())).cookie(new NewCookie(AUTH_COOKIE, cookie.getValue())).build();
    }

    @GET
    @Path("/bookings/{id}")
    public Response getBookingsById(@PathParam("id") Long id, @CookieParam(AUTH_COOKIE) Cookie cookie){

        if (cookie == null) { //Haven't login
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();

            TypedQuery<User> userQuery = em.createQuery("select u from User u where u.cookie = :cookie", User.class)
                    .setParameter("cookie", cookie.getValue());

            List<User> users = userQuery.getResultList();

            if (users == null) { //Haven't sign up
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            LOGGER.info("Login successful");
            User user = users.get(0);

            Booking booking = em.find(Booking.class, id);

            if (booking == null) { //Didn't have bookings
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            if (booking.getUser().getId() != user.getId()) { //Check if belongs to mine
                return Response.status(Response.Status.FORBIDDEN).build();
            }

            em.getTransaction().commit();

            return Response.ok(BookingMapper.toDTO(booking))
                    .cookie(new NewCookie(AUTH_COOKIE, cookie.getValue())).build();
        } finally {
            em.close();
        }

    }

    @GET
    @Path("/bookings")
    public Response getAllBookingsForUser(@CookieParam("auth") Cookie cookie) {
        LOGGER.info("Get all the bookings for user");
        if (cookie == null) { //User hasn't login yet
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {

            em.getTransaction().begin();

            TypedQuery<User> userQuery = em.createQuery("select u from User u where u.cookie = :cookie", User.class)
                    .setParameter("cookie", cookie.getValue());

            List<User> users = userQuery.getResultList();

            if (users == null) { //Haven't sign up
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            LOGGER.info("Login successful");
            User user = users.get(0);

            List<Booking> bookings = em.createQuery("select b from Booking b where b.user = :user", Booking.class)
                    .setParameter("user", user).getResultList();

            GenericEntity<List<BookingDTO>> entity = new GenericEntity<>(BookingMapper.listToDTO(bookings)){};

            em.getTransaction().commit();
            return Response.ok(entity).cookie(new NewCookie(AUTH_COOKIE, cookie.getValue())).build();

        } finally {
            em.close();
        }
    }


}
