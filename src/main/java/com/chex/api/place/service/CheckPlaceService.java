package com.chex.api.place.service;

import com.chex.api.place.AchievedPlaceDTO;
import com.chex.api.place.response.CheckPlaceRequest;
import com.chex.api.place.response.CheckPlaceResponse;
import com.chex.api.place.response.CheckPlaceResponseStatus;
import com.chex.modules.places.model.Coords;
import com.chex.modules.places.model.Place;
import com.chex.user.model.VisitedPlace;
import com.chex.modules.places.repository.PlaceRepository;
import com.chex.user.repository.VisitedPlacesRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class CheckPlaceService {
    private static final double latitudeOffset = 0.0012;
    private static final double longitudeOffset = 0.0012;

    private final PlaceRepository placeRepository;
    private final VisitedPlacesRepository visitedPlacesRepository;

    @Autowired
    public CheckPlaceService(PlaceRepository placeRepository, VisitedPlacesRepository visitedPlacesRepository) {
        this.placeRepository = placeRepository;
        this.visitedPlacesRepository = visitedPlacesRepository;
    }

    public List<Place> checkPlace(CheckPlaceRequest request, Long userid, CheckPlaceResponse checkPlaceResponse) {
        List<Place> places = filterPlace(request.getCoords());

        if(places.isEmpty()) {
            checkPlaceResponse.setResponseStatus(CheckPlaceResponseStatus.NOTFOUND);
            return null;
        }

        List<Place> inRange = CalculateCoords.isInRange(request.getCoords(), places);
        if(inRange.isEmpty()){
            checkPlaceResponse.setResponseStatus(CheckPlaceResponseStatus.NOTFOUND);
            return null;
        }

        inRange = removeReachedPlaces(inRange, userid);
        if(inRange.isEmpty()){
            checkPlaceResponse.setResponseStatus(CheckPlaceResponseStatus.ALREADYEXISTS);
            return null;
        }

        checkPlaceResponse.setResponseStatus(CheckPlaceResponseStatus.FOUND);
        return findParent(inRange, userid);
    }

    public List<Place> removeReachedPlaces(List<Place> inRange, Long userid){
        List<Place> newlist = new ArrayList<>();
        for(Place p : inRange){
            if(!this.visitedPlacesRepository.existsByUseridAndPlaceid(userid, p.getId())){
                newlist.add(p);
            }
        }
        return newlist;
    }

    private List<Place> findParent(List<Place> inRange, Long userid){

        String id = inRange.get(0).getId();
        String[] splitedId = id.split("\\.");

        for(int i = 4; i > 0; i--){
            int length = splitedId[i].length();
            splitedId[i] = "0".repeat(length);
            String newid = StringUtils.join(splitedId, ".");

            if(!this.visitedPlacesRepository.existsByUseridAndPlaceid(userid, newid)){
                Optional<Place> oPlace = this.placeRepository.findById(newid);
                if(oPlace.isEmpty())
                    throw new IllegalArgumentException();
                inRange.add(oPlace.get());
            }
        }
        return inRange;
    }

    public List<Place> filterPlace(Coords currentCoords){
        List<Coords> newcoords = coordsWithoffset(currentCoords);
        Coords from = newcoords.get(0);
        Coords to = newcoords.get(1);

        return placeRepository.filterCoords(to.latitude, from.latitude, from.longitude, to.longitude);
    }

    private List<Coords> coordsWithoffset(Coords currentCoords) {
        Coords coordsfrom = new Coords(currentCoords.latitude, currentCoords.longitude);
        Coords coordsTo = new Coords(currentCoords.latitude, currentCoords.longitude);

        coordsTo.latitude = BigDecimal.valueOf(currentCoords.latitude + latitudeOffset).setScale(6, RoundingMode.HALF_UP).doubleValue();
        coordsfrom.latitude = BigDecimal.valueOf(currentCoords.latitude - latitudeOffset).setScale(6, RoundingMode.HALF_UP).doubleValue();

        coordsTo.longitude = BigDecimal.valueOf(currentCoords.longitude + longitudeOffset).setScale(6, RoundingMode.HALF_UP).doubleValue();
        coordsfrom.longitude = BigDecimal.valueOf(currentCoords.longitude - longitudeOffset).setScale(6, RoundingMode.HALF_UP).doubleValue();

        return Arrays.asList(coordsfrom, coordsTo);
    }

    @Transactional
    public int addToUserVisitedPlaces(AchievedPlaceDTO dto, Long userid){

        int gainedExperience = 0;

        for(Map.Entry<String, Integer> p : dto.getAchievedPlaces().entrySet()){
            VisitedPlace visitedPlace = new VisitedPlace(userid);
            visitedPlace.setPlaceid(p.getKey());
            visitedPlace.setRating(p.getValue());
            visitedPlace.setVdate(dto.getTimestamp());
            this.visitedPlacesRepository.save(visitedPlace);

            if(p.getValue() > 0) {
                Place place = this.placeRepository.getById(visitedPlace.getPlaceid());
                place.addVote(p.getValue());
                this.placeRepository.save(place);

                gainedExperience += place.getPoints();
            }
        }
        return gainedExperience;
    }

}
