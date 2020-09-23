package se325.assignment01.concert.service.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se325.assignment01.concert.common.dto.ConcertDTO;
import se325.assignment01.concert.common.dto.ConcertSummaryDTO;
import se325.assignment01.concert.common.dto.PerformerDTO;
import se325.assignment01.concert.common.dto.UserDTO;
import se325.assignment01.concert.service.domain.Concert;
import se325.assignment01.concert.service.domain.Performer;
import se325.assignment01.concert.service.domain.User;
import se325.assignment01.concert.service.jaxrs.LocalDateTimeParam;
import se325.assignment01.concert.service.mapper.ConcertMapper;
import se325.assignment01.concert.service.mapper.PerformerMapper;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.List;
import java.util.UUID;

@Path("/concert-service")
public class ConcertResource {
    private static Logger LOGGER = LoggerFactory.getLogger(ConcertResource.class);

    @GET
    @Path("/concerts/{id}")
    @Produces(MediaType.APPLICATION_JSON)
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
    @Produces(MediaType.APPLICATION_JSON)
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
    @Produces(MediaType.APPLICATION_JSON)
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
    @Produces(MediaType.APPLICATION_JSON)
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
    @Produces(MediaType.APPLICATION_JSON)
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

    @GET
    @Path("/seats/{date}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSeatsForDate(@PathParam("date") LocalDateTimeParam date, @QueryParam("status") String status) {
        LOGGER.info("Get the seats of date " + date + "with status:" + status);
        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();

            return null;
        } finally {
            em.close();
        }
    }

}
