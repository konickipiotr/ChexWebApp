package com.chex.webapp.admin.places.newplace;

import com.chex.modules.category.Category;
import com.chex.modules.category.CategoryRepository;
import com.chex.modules.places.*;
import com.chex.utils.Duo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AddPlaceService {

    private final CategoryRepository categoryRepository;
    private final PlaceRepository placeRepository;
    private final PlaceNameRepository placeNameRepository;
    private final PlaceDescriptionRepository placeDescriptionRepository;

    @Autowired
    public AddPlaceService(CategoryRepository categoryRepository, PlaceRepository placeRepository, PlaceNameRepository placeNameRepository, PlaceDescriptionRepository placeDescriptionRepository) {
        this.categoryRepository = categoryRepository;
        this.placeRepository = placeRepository;
        this.placeNameRepository = placeNameRepository;
        this.placeDescriptionRepository = placeDescriptionRepository;
    }

    public boolean idAlreadyExists(PlaceForm placeForm){
        return this.placeRepository.existsById(placeForm.createId());
    }

    public void addNewPlace(PlaceForm placeForm){
        Place place = new Place(placeForm);
        place.setCategory(setCategoryAuto(placeForm));
        this.placeRepository.save(place);
        this.placeNameRepository.save(new PlaceName(place.getId(), placeForm));
        this.placeDescriptionRepository.save(new PlaceDescription(place.getId(), placeForm));
    }

    public List<Duo<String>> getListOfPlaces(String id, PlaceType placeType, String lang){
        List<Duo<String>> list = new ArrayList<>();
        List<Place> tmpList;
        switch (placeType){
            case COUNTRY: tmpList = this.placeRepository.getAllCountriesFromContinent(id);break;
            case PROVINCE: tmpList = this.placeRepository.getAllProvincesFromCountry(id); break;
            case REGION: tmpList = this.placeRepository.getRegionsFromProvince(id); break;
            case OTHER: return new ArrayList<>();
            default:
                throw new IllegalArgumentException("Wrong placeType");
        }

        for(Place p : tmpList){
            PlaceName placeName = this.placeNameRepository.getById(p.getId());
            if("pl".equals(lang))
                list.add(new Duo<>(p.getId(), placeName.getPl()));
            else if("en".equals(lang))
                list.add(new Duo<>(p.getId(), placeName.getEng()));
            else
                throw new IllegalArgumentException("Illegal language parameter");
        }

        Collections.sort(list);
        if(placeType.equals(PlaceType.REGION))
            moveRegionAtTop(list);
        return list;
    }

    private void moveRegionAtTop(List<Duo<String>> list){
        for(int i = 0; i < list.size(); i++){
            if(list.get(i).getValue().equals("Region")){
                Collections.swap(list, i, 0);
                break;
            }
        }
    }

    private Map<Long, String> getAllCategories(String lang){
        List<Category> categories = this.categoryRepository.findAll();
        Map<Long, String> out = new TreeMap<>();
        if(!categories.isEmpty()){
            for(Category c : categories){
                if(lang.equals("pl"))
                    out.put(c.getId(), c.getPl());
                if(lang.equals("en"))
                    out.put(c.getId(), c.getEng());
            }
        }
        return out;
    }


    public boolean addNewGeneralPlace(PlaceForm placeForm){
        if(this.placeRepository.existsById(placeForm.createId()))
            return false;

        Place place = new Place(placeForm);
        place.setCategory(setCategoryAuto(placeForm));
        this.placeRepository.save(place);
        this.placeNameRepository.save(new PlaceName(place.getId(), placeForm));
        this.placeDescriptionRepository.save(new PlaceDescription(place.getId(), placeForm));
        return true;
    }

    private Long setCategoryAuto(PlaceForm pf){
        int prefixLen = pf.getPrefix().length();
        if(prefixLen == 0)
            return this.categoryRepository.findByEng("continent").getId();
        else if(prefixLen <= 2)
            return this.categoryRepository.findByEng("country").getId();
        else if(prefixLen <= 6)
            return this.categoryRepository.findByEng("province").getId();
        else if(prefixLen <= 10){
            if(pf.getPrefix().equals("REG"))
                return this.categoryRepository.findByEng("region").getId();
            else
                return this.categoryRepository.findByEng("city").getId();
        }else
            return pf.getCategory();
    }
}
