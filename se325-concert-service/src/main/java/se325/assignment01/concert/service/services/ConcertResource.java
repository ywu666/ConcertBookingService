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
import javax.persistence.PostLoad;
import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Path("/concert-service")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConcertResource {
    private static Logger LOGGER = LoggerFactory.getLogger(ConcertResource.class);
    public static final String AUTH_COOKIE = "auth";
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
            List<Concert> concertList = em.createQuery("select c from Concert c", Concert.class).getResultList();
            List<ConcertDTO> concertDTOList = ConcertMapper.listToDTO(concertList);
            GenericEntity<List<ConcertDTO>> entity = new GenericEntity<>(concertDTOList) {};
            return Response.ok(entity).build();
        } finally {
            em.getTransaction().commit();
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
            List<Concert> concerts = em.createQuery("select c from Concert c", Concert.class).getResultList();
            List<ConcertSummaryDTO> concertSummaryDTOList = ConcertMapper.listToConcertSummaryDTO(concerts);

            GenericEntity<List<ConcertSummaryDTO>> entity =  new GenericEntity<>(concertSummaryDTOList) {};
            return Response.ok(entity).build();
        } finally {
            em.getTransaction().commit();
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
            }
            return Response.ok(PerformerMapper.toDTO(performer)).build();
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
            List<Performer> performers = em.createQuery("select performer from Performer performer", Performer.class).getResultList();
            List<PerformerDTO> performerDTOList = PerformerMapper.listToDTO(performers);
            GenericEntity<List<PerformerDTO>> entity = new GenericEntity<>(performerDTOList) {};

            return Response.ok(entity).build();
        } finally {
            em.getTransaction().commit();
            em.close();
        }
    }


    @POST
    @Path("/login")
    public Response login(UserDTO userDTO) {
        LOGGER.info("Try to log in with username ans password.");
        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();
            TypedQuery<User> queryForUser = em.createQuery("select user from User user where user.username = :username AND user.password = :password", User.class)
                    .setParameter("username", userDTO.getUsername())
                    .setParameter("password", userDTO.getPassword());

            List<User> users = queryForUser.getResultList();

            if(users.isEmpty()) { //The username is not found or the password is incorrect
                return Response.status(Response.Status.UNAUTHORIZED).build();
            } else {
                User user = users.get(0);
                String token = UUID.randomUUID().toString();
                user.setCookie(token);
                em.merge(user);
                em.getTransaction().commit();
                return Response.ok().cookie(new NewCookie("auth", token)).build();
            }
        } finally {
            em.close();
        }
    }

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
            User user = this.getAuthenticatedUser(em, cookie);

            if (user == null) { //Haven't sign up
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }


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

            //Get all the booked seats
            List<Seat> seatsList = em.createQuery("select s from Seat s where s.date = :requestDate and s.isBooked = true ", Seat.class)
                    .setParameter("requestDate", dto.getDate()).getResultList();

            int numOfBookedSeats = seatsList.size();

            newBooking = new Booking(dto.getConcertId(), dto.getDate(), seats, user);
            em.persist(newBooking);
            em.getTransaction().commit();

            this.notifyConcertInfo(dto.getConcertId(), numOfBookedSeats, dto.getDate());
            return Response.created(URI.create("concert-service/bookings/"+ newBooking.getId())).cookie(new NewCookie(AUTH_COOKIE, cookie.getValue())).build();
        } finally {
            em.close();
        }
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

            User user = this.getAuthenticatedUser(em, cookie);

            if (user == null) { //Haven't sign up
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

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

            User user = this.getAuthenticatedUser(em, cookie);

            if (user == null) { //Haven't sign up
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            List<Booking> bookings = em.createQuery("select b from Booking b where b.user = :user", Booking.class)
                    .setParameter("user", user).getResultList();

            GenericEntity<List<BookingDTO>> entity = new GenericEntity<>(BookingMapper.listToDTO(bookings)){};

            em.getTransaction().commit();
            return Response.ok(entity).cookie(new NewCookie(AUTH_COOKIE, cookie.getValue())).build();

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
            List<Seat> seats;

            if(status == null || status == BookingStatus.Any) { //All seats for that date
                seats = em.createQuery("select seat from Seat seat where seat.date = :date",Seat.class)
                        .setParameter("date", date)
                        .getResultList();
            } else {
                boolean isBooked = (status == BookingStatus.Booked);
                seats = em.createQuery("select seat from Seat seat where seat.date = :date AND seat.isBooked = :isBooked ",Seat.class)
                        .setParameter("date", date)
                        .setParameter("isBooked", isBooked)
                        .getResultList();
            }

            List<SeatDTO> seatDTOList = SeatMapper.listToDTO(seats);
            GenericEntity<List<SeatDTO>> entity = new GenericEntity<>(seatDTOList) {};
            return Response.ok(entity).build();
        } finally {
            em.close();
        }
    }

    @POST
    @Path("/subscribe/concertInfo")
    public void subscriptConcertInfo(@CookieParam("auth") Cookie cookie, ConcertInfoSubscriptionDTO dto, @Suspended AsyncResponse response) {
        LOGGER.info("Attempting to subscribe");

        if(cookie == null) {
            response.resume(Response.status(Response.Status.UNAUTHORIZED).build());
            return;
        }

        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();
            User user = this.getAuthenticatedUser(em, cookie);

            if (user == null) {
                response.resume(Response.status(Response.Status.UNAUTHORIZED).build());
                return;
            }

            Concert concert = em.find(Concert.class, dto.getConcertId());

            if (concert == null) { // No concert
                response.resume(Response.status(Response.Status.BAD_REQUEST).build());
                return;
            }

            if (!concert.getDates().contains(dto.getDate())) { //The date is wrong
                response.resume(Response.status(Response.Status.BAD_REQUEST).build());
                return;
            }

            List<Subscription> subscribers = subscribersMap.getOrDefault(concert.getId(), new ArrayList<>());
            subscribers.add(new Subscription(dto, response));
            subscribersMap.put(concert.getId(),subscribers);
        } finally {
            em.close();
        }
    }


    @POST
    public void notifyConcertInfo (Long concertID, int numOfBookedSeats, LocalDateTime date) {
        List<Subscription> subs = subscribersMap.get(concertID);

        if(subs != null) {
            List<Subscription> subs2 = new ArrayList<>();
            for(Subscription sub:subs) {
                if((sub.getDto().getDate().equals(date))
                        && (sub.getDto().getPercentageBooked() < (100 * numOfBookedSeats/ TheatreLayout.NUM_SEATS_IN_THEATRE))) {
                        AsyncResponse response = sub.getResponse();
                        int numOfAvaliableSeats = TheatreLayout.NUM_SEATS_IN_THEATRE - numOfBookedSeats;

                        synchronized (response) {
                            ConcertInfoNotificationDTO notification = new ConcertInfoNotificationDTO(numOfAvaliableSeats);
                            response.resume(Response.ok(notification).build());
                        }
                } else {
                    subs2.add(sub);
                }
            }
            subscribersMap.put(concertID, subs2);
        }

    }


    //===========HELP METHODS =========
    public User getAuthenticatedUser(EntityManager em, Cookie cookie) {
        TypedQuery<User> userQuery = em.createQuery("select u from User u where u.cookie = :cookie", User.class)
                .setParameter("cookie", cookie.getValue());

        List<User> users = userQuery.getResultList();

        if (users == null) { //Haven't sign up
           return null;
        }

        LOGGER.info("Login successful");
        User user = users.get(0);
        return user;
    }

}
