package se325.assignment01.concert.service.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se325.assignment01.concert.common.dto.*;
import se325.assignment01.concert.common.types.BookingStatus;
import se325.assignment01.concert.service.domain.*;
import se325.assignment01.concert.service.jaxrs.LocalDateTimeParam;
import se325.assignment01.concert.service.mapper.BookingMapper;
import se325.assignment01.concert.service.mapper.ConcertMapper;
import se325.assignment01.concert.service.mapper.PerformerMapper;
import se325.assignment01.concert.service.mapper.SeatMapper;
import se325.assignment01.concert.service.util.Subscription;
import se325.assignment01.concert.service.util.TheatreLayout;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Path("/concert-service")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConcertResource {
    private static Logger LOGGER = LoggerFactory.getLogger(ConcertResource.class);
    private static final Map<Long, List<Subscription>> subscribersMap = new HashMap<>();

    @GET
    @Path("/concerts/{id}")
    public Response getConcertById(@PathParam("id") Long id) {
        LOGGER.info("Receiving a concert with id " + id);
        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();

            Concert concert = em.find(Concert.class, id);

            em.getTransaction().commit();

            if (concert == null) { //No existing concert
                LOGGER.debug("No concert with id: " + id + " exists");
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            return Response.ok(ConcertMapper.toDTO(concert)).build();
        } finally {
            em.close();
        }
    }

    @GET
    @Path("/concerts")
    public Response getAllConcerts() {
        LOGGER.info("Getting all concerts.");
        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();

            List<Concert> concertList = em.createQuery("select c from Concert c", Concert.class)
                    .getResultList();

            em.getTransaction().commit();

            List<ConcertDTO> concertDTOList = ConcertMapper.listToDTO(concertList);
            GenericEntity<List<ConcertDTO>> entity = new GenericEntity<>(concertDTOList) {};
            return Response.ok(entity).build();
        } finally {
            em.close();
        }

    }

    @GET
    @Path("/concerts/summaries")
    public Response getConcertSummaries() {
        LOGGER.info("Getting the concert summaries.");
        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();

            List<Concert> concerts = em.createQuery("select c from Concert c", Concert.class)
                    .getResultList();

            em.getTransaction().commit();

            List<ConcertSummaryDTO> dtos = ConcertMapper.listToConcertSummaryDTO(concerts);
            GenericEntity<List<ConcertSummaryDTO>> entity =  new GenericEntity<>(dtos) {};
            return Response.ok(entity).build();
        } finally {

            em.close();
        }
    }

    @GET
    @Path("/performers/{id}")
    public Response getPerformerById(@PathParam("id") Long id) {
        LOGGER.info("Getting a performer of id " + id);
        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();

            Performer performer = em.find(Performer.class, id);

            em.getTransaction().commit();

            if (performer == null) {
                LOGGER.debug("No performer with id: " + id + " exists");
                return Response.status(Response.Status.NOT_FOUND).build();
            } else {
                return Response.ok(PerformerMapper.toDTO(performer)).build();
            }

        } finally {
            em.close();
        }

    }

    @GET
    @Path("/performers")
    public Response getAllPerformers() {
        LOGGER.info("Getting all performers.");
        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();

            List<Performer> performers = em.createQuery("select performer from Performer performer", Performer.class)
                    .getResultList();

            em.getTransaction().commit();

            List<PerformerDTO> dtos = PerformerMapper.listToDTO(performers);
            GenericEntity<List<PerformerDTO>> entity = new GenericEntity<>(dtos) {};
            return Response.ok(entity).build();
        } finally {
            em.close();
        }
    }


    @POST
    @Path("/login")
    public Response login(UserDTO userDTO) {
        LOGGER.info("Try to log in with username ans password. " + userDTO.getUsername());
        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();

            List<User> users  = em.createQuery("select user from User user where user.username = :username and user.password = :password", User.class)
                    .setParameter("username", userDTO.getUsername())
                    .setParameter("password", userDTO.getPassword())
                    .getResultList();

            if (users.isEmpty()) { //The username is not found or the password is incorrect
                return Response.status(Response.Status.UNAUTHORIZED).build();
            } else {
                User user = users.get(0); //Get the user
                String token = UUID.randomUUID().toString(); //Generate the token
                user.setCookie(token);
                em.merge(user);

                em.getTransaction().commit();

                //Send the cookie back to the client
                return Response.ok().cookie(new NewCookie("auth", token)).build();
            }
        } finally {
            em.close();
        }
    }

    @POST
    @Path("/bookings")
    public Response makeABooking(BookingRequestDTO dto, @CookieParam("auth") Cookie cookie) {
        LOGGER.info("Try to make a booing of concert " + dto.getConcertId()
                + "on Date " + dto.getDate());

        if (cookie == null) { //Haven't login
            LOGGER.debug("Didn't provide the cookie");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();
            User user = this.getAuthenticatedUser(em, cookie);

            if (user == null) { //Haven't sign up
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            Concert concert = em.find(Concert.class, dto.getConcertId());

            //Invalid the concert and valid concert with invalid dates
            if (concert == null || !concert.getDates().contains(dto.getDate())) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            //Get all the requested seats and set the lock to prevent the concurrent booking
            List<Seat> seats = em.createQuery("select s from Seat s where s.date = :date and s.isBooked = false and s.label in :seats", Seat.class)
                    .setLockMode(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
                    .setParameter("seats", dto.getSeatLabels())
                    .setParameter("date", dto.getDate())
                    .getResultList();

            //At least one seat is already booked
            if (seats.size() != dto.getSeatLabels().size()) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }

            for (Seat seat : seats) { //Book all seats
                seat.setBooked(true);
            }

            //Get all the booked seats
            List<Seat> bookedSeatsList = em.createQuery("select s from Seat s where s.date = :date and s.isBooked = true ", Seat.class)
                    .setParameter("date", dto.getDate())
                    .getResultList();

            //Get the number of booked seats
            int numOfBookedSeats = bookedSeatsList.size();

            //Set the booking
            Booking newBooking = new Booking(dto.getConcertId(), dto.getDate(), seats, user);
            em.persist(newBooking);

            em.getTransaction().commit();

            this.notifyConcertInfo(dto, numOfBookedSeats);
            return Response.created(URI.create("concert-service/bookings/" + newBooking.getId()))
                    .cookie(new NewCookie("auth", cookie.getValue())).build();
        } finally {
            em.close();
        }
    }

    @GET
    @Path("/bookings/{id}")
    public Response getOwnBookingById(@PathParam("id") Long id, @CookieParam("auth") Cookie cookie){

        if (cookie == null) { //Haven't login
            LOGGER.debug("Didn't provide the cookie");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();

            User user = this.getAuthenticatedUser(em, cookie);

            if (user == null) { //Haven't sign up
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            Booking booking = em.find(Booking.class, id); //Find the booking of that id

            if (booking == null) { //Didn't have bookings
                LOGGER.debug("No booking of id " + id);
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            if (booking.getUser().getId() != user.getId()) { //Check if belongs to mine
                LOGGER.debug("The booking is from others.");
                return Response.status(Response.Status.FORBIDDEN).build();
            }

            em.getTransaction().commit();

            return Response.ok(BookingMapper.toDTO(booking))
                    .cookie(new NewCookie("auth", cookie.getValue())).build();
        } finally {
            em.close();
        }
    }

    @GET
    @Path("/bookings")
    public Response getAllBookingsForUser(@CookieParam("auth") Cookie cookie) {
        LOGGER.info("Get all the bookings for auth user");

        if (cookie == null) { //User hasn't login yet
            LOGGER.debug("Didn't provide the cookie");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();

            User user = this.getAuthenticatedUser(em, cookie);

            if (user == null) { //Haven't sign up
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            List<Booking> bookings = em.createQuery("select b from Booking b where b.user = :user", Booking.class)
                    .setParameter("user", user)
                    .getResultList();

            GenericEntity<List<BookingDTO>> entity = new GenericEntity<>(BookingMapper.listToDTO(bookings)){};

            em.getTransaction().commit();

            return Response.ok(entity)
                    .cookie(new NewCookie("auth", cookie.getValue())).build();
        } finally {
            em.close();
        }
    }

    @GET
    @Path("/seats/{date}")
    public Response getSeats(@PathParam("date") LocalDateTimeParam dateTimeParam, @QueryParam("status") BookingStatus status) {
        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();
            LocalDateTime  date = dateTimeParam.getLocalDateTime();
            List<Seat> seats = new ArrayList<>();

            if(status != null) {
                if(status == BookingStatus.Any) { //All seats for that date
                    seats = em.createQuery("select seat from Seat seat where seat.date = :date",Seat.class)
                            .setParameter("date", date)
                            .getResultList();
                } else { //Getting booked or unbooked seats
                    seats = em.createQuery("select seat from Seat seat where seat.date = :date and seat.isBooked = :isBooked ",Seat.class)
                            .setParameter("date", date)
                            .setParameter("isBooked", status == BookingStatus.Booked)
                            .getResultList();
                }
            }

            em.getTransaction().commit();

            GenericEntity<List<SeatDTO>> entity = new GenericEntity<>(SeatMapper.listToDTO(seats)) {};
            return Response.ok(entity).build();
        } finally {
            em.close();
        }
    }

    @POST
    @Path("/subscribe/concertInfo")
    public void subscriptConcertInfo(@CookieParam("auth") Cookie cookie, ConcertInfoSubscriptionDTO dto, @Suspended AsyncResponse sub) {
        LOGGER.info("Attempting to subscribe");

        if(cookie == null) {
            sub.resume(Response.status(Response.Status.UNAUTHORIZED).build());
            return;
        }

        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();

            User user = this.getAuthenticatedUser(em, cookie);

            if (user == null) {
                sub.resume(Response.status(Response.Status.UNAUTHORIZED).build());
                return;
            }

            Concert concert = em.find(Concert.class, dto.getConcertId());

            if (concert == null || !concert.getDates().contains(dto.getDate())) {
                sub.resume(Response.status(Response.Status.BAD_REQUEST).build());
                return;
            }

            em.getTransaction().commit();

            //Renew the subscribers map
            List<Subscription> subscribers = subscribersMap.getOrDefault(concert.getId(), new ArrayList<>());
            subscribers.add(new Subscription(dto, sub));
            subscribersMap.put(concert.getId(), subscribers);
        } finally {
            em.close();
        }
    }

    public void notifyConcertInfo(BookingRequestDTO dto, int numOfBookedSeats) {
        List<Subscription> subs = subscribersMap.get(dto.getConcertId());

        if(subs != null) { //Make sure the concertID is valid
            List<Subscription> subs2 = new ArrayList<>();
            int numOfAvaliableSeats = TheatreLayout.NUM_SEATS_IN_THEATRE - numOfBookedSeats;

            for(Subscription sub:subs) {
                if( (sub.getDto().getDate().equals(dto.getDate()))
                        && (sub.getDto().getPercentageBooked() < this.calPercentage(numOfBookedSeats))) {
                        AsyncResponse response = sub.getResponse();

                        synchronized (response) {
                            ConcertInfoNotificationDTO notification = new ConcertInfoNotificationDTO(numOfAvaliableSeats);
                            response.resume(Response.ok(notification).build());
                        }
                } else {
                    subs2.add(sub);
                }
            }
            subscribersMap.put(dto.getConcertId(), subs2);
        }

    }

    //=========== HELP METHODS =========
    public User getAuthenticatedUser(EntityManager em, Cookie cookie) {
        List<User> users = em.createQuery("select u from User u where u.cookie = :cookie", User.class)
                .setParameter("cookie", cookie.getValue())
                .getResultList();

        if (users == null) { //Haven't sign up
            LOGGER.debug("Didn't find the user with cookie.");
            return null;
        }

        LOGGER.info("Login successful");
        User user = users.get(0);
        return user;
    }

    public int calPercentage(int numOfBookedSeats) {
       return ( 100 * numOfBookedSeats / TheatreLayout.NUM_SEATS_IN_THEATRE);
    }
}
