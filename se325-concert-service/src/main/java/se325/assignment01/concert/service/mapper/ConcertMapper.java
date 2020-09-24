package se325.assignment01.concert.service.mapper;

import se325.assignment01.concert.common.dto.ConcertDTO;
import se325.assignment01.concert.common.dto.ConcertSummaryDTO;
import se325.assignment01.concert.service.domain.Concert;

import java.util.ArrayList;
import java.util.List;

public class ConcertMapper {
    public static ConcertDTO toDTO(Concert concert){
        ConcertDTO result = new ConcertDTO(concert.getId(),concert.getTitle(),concert.getImageName(),concert.getBlurb());
        result.setDates(new ArrayList<>(concert.getDates()));
        result.setPerformers(PerformerMapper.listToDTO(concert.getPerformers()));
        return result;
    }

    public static List<ConcertDTO> listToDTO(List<Concert> concerts) {
        List<ConcertDTO> dtoList = new ArrayList<>();
        for (Concert c : concerts) {
            dtoList.add(ConcertMapper.toDTO(c));
        }
        return dtoList;
    }

    public static List<ConcertSummaryDTO> listToConcertSummaryDTO(List<Concert> concerts) {
        List<ConcertSummaryDTO> concertSummaryDTOList = new ArrayList<>();

        for(Concert c:concerts) {
            concertSummaryDTOList.add( new ConcertSummaryDTO(c.getId(), c.getTitle(), c.getImageName()));
        }

        return concertSummaryDTOList;
    }
}
