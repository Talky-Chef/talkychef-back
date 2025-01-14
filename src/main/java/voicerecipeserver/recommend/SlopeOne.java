package voicerecipeserver.recommend;

import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import voicerecipeserver.config.Constants;
import voicerecipeserver.model.dto.RecipeDto;
import voicerecipeserver.model.entities.Mark;
import voicerecipeserver.model.entities.Recipe;
import voicerecipeserver.model.entities.User;
import voicerecipeserver.model.exceptions.NotFoundException;
import voicerecipeserver.respository.MarkRepository;
import voicerecipeserver.respository.RecipeRepository;
import voicerecipeserver.respository.UserRepository;
import voicerecipeserver.security.service.impl.AuthServiceCommon;
import voicerecipeserver.utils.FindUtils;
import voicerecipeserver.utils.GetUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.StreamSupport;

/**
 * Slope One algorithm implementation
 */
@Service
public class SlopeOne {

    private final ModelMapper mapper;
    private final UserRepository userRepository;
    private final MarkRepository markRepository;
    private final Map<Recipe, Map<Recipe, Double>> diff = new HashMap<>();
    private final Map<Recipe, Map<Recipe, Integer>> freq = new HashMap<>();
    private final Map<User, HashMap<Recipe, Double>> outputData = new HashMap<>();
    private final RecipeRepository recipeRepository;

    @Autowired
    public SlopeOne(ModelMapper mapper, UserRepository userRepository, MarkRepository markRepository,
                    RecipeRepository recipeRepository) {
        this.mapper = mapper;
        this.userRepository = userRepository;
        this.markRepository = markRepository;
        this.recipeRepository = recipeRepository;
    }

    public List<RecipeDto> recommendAlgSlopeOne(Integer limit, Integer page) throws NotFoundException {
        Map<User, HashMap<Recipe, Double>> inputData = initializeData();
        buildDifferencesMatrix(inputData);
        return predict(inputData, GetUtil.getCurrentLimit(limit), GetUtil.getCurrentPage(page));
    }

    private Map<User, HashMap<Recipe, Double>> initializeData() {
        List<Mark> markList = StreamSupport.stream(markRepository.findAll().spliterator(), false).toList();
        Map<User, HashMap<Recipe, Double>> data = new HashMap<>();
        for (Mark m : markList) {
            HashMap<Recipe, Double> userRecipesMarked;
            if (data.containsKey(m.getUser())) {
                userRecipesMarked = data.get(m.getUser());
            } else {
                userRecipesMarked = new HashMap<>();
            }
            userRecipesMarked.put(m.getRecipe(), (double) m.getMark());
            data.put(m.getUser(), userRecipesMarked);
        }
        return data;
    }

    /**
     * Based on the available data, calculate the relationships between the
     * items and number of occurences
     *
     * @param data existing user data and their items' ratings
     */
    private void buildDifferencesMatrix(Map<User, HashMap<Recipe, Double>> data) {
        data.values().forEach(mark -> {
            mark.forEach((recipe1, rating1) -> {
                if (!diff.containsKey(recipe1)) {
                    diff.put(recipe1, new HashMap<>());
                    freq.put(recipe1, new HashMap<>());
                }
                mark.forEach((recipe2, rating2) -> {
                    int oldCount = freq.get(recipe1).getOrDefault(recipe2, 0);
                    double oldDiff = diff.get(recipe1).getOrDefault(recipe2, 0.0);
                    double observedDiff = rating1 - rating2;
                    freq.get(recipe1).put(recipe2, oldCount + 1);
                    diff.get(recipe1).put(recipe2, oldDiff + observedDiff);
                });
            });
        });

        diff.forEach((recipe1, innerMap) -> {
            innerMap.forEach((recipe2, value) -> {
                int count = freq.get(recipe1).get(recipe2);
                innerMap.put(recipe2, value / count);
            });
        });
    }

    /**
     * Based on existing data predict all missing ratings. If prediction is not
     * possible, the value will be equal to -1
     *
     * @param data  existing user data and their items' ratings
     */
    private List<RecipeDto> predict(Map<User, HashMap<Recipe, Double>> data, int limit, int page) throws NotFoundException {
        // Initialize the uPred and uFreq maps
        HashMap<Recipe, Double> uPred = new HashMap<>();
        HashMap<Recipe, Integer> uFreq = new HashMap<>();
        diff.keySet().forEach(j -> {
            uFreq.put(j, 0);
            uPred.put(j, 0.0);
        });

        for (Entry<User, HashMap<Recipe, Double>> e : data.entrySet()) {
            updateUPredAndUFreq(e, uPred, uFreq);
            HashMap<Recipe, Double> clean = calculateCleanMap(uPred, uFreq);
            fillMissingRatings(e, clean);
            outputData.put(e.getKey(), clean);
        }

        return getSortedRecipeDtos(limit, page);
    }

    private void updateUPredAndUFreq(Entry<User, HashMap<Recipe, Double>> e,
                                     HashMap<Recipe, Double> uPred, HashMap<Recipe, Integer> uFreq) {
        for (Recipe j : e.getValue().keySet()) {
            for (Recipe k : diff.keySet()) {
                double diffValue = diff.get(k).getOrDefault(j, 0.0);
                double userValue = e.getValue().get(j);
                int freqValue = freq.get(k).getOrDefault(j, 0);

                double predictedValue = diffValue + userValue;
                double finalValue = predictedValue * freqValue;
                uPred.put(k, uPred.getOrDefault(k, 0.0) + finalValue);
                uFreq.put(k, uFreq.getOrDefault(k, 0) + freqValue);
            }
        }
    }

    private HashMap<Recipe, Double> calculateCleanMap(HashMap<Recipe, Double> uPred, HashMap<Recipe, Integer> uFreq) {
        HashMap<Recipe, Double> clean = new HashMap<>();
        uPred.keySet().forEach(j -> {
            if (uFreq.get(j) > 0) {
                clean.put(j, uPred.get(j) / uFreq.get(j));
            }
        });
        return clean;
    }

    private void fillMissingRatings(Entry<User, HashMap<Recipe, Double>> e, HashMap<Recipe, Double> clean) {
        List<Mark> markList = StreamSupport.stream(markRepository.findAll().spliterator(), false).toList();
        markList.forEach(j -> {
            Recipe recipe = j.getRecipe();
            if (e.getValue().containsKey(recipe)) {
                clean.put(recipe, e.getValue().get(recipe));
            } else if (!clean.containsKey(recipe)) {
                clean.put(recipe, -1.0);
            }
        });
    }

    private List<RecipeDto> getSortedRecipeDtos(int limit, int page) throws NotFoundException {
        List<RecipeDto> recipeDtos;
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken)) {
            User user = FindUtils.findUserByUid(userRepository, AuthServiceCommon.getUserLogin());
            HashMap<Recipe, Double> outputUserData = outputData.get(user);
            if (outputUserData != null) {
                List<Recipe> sortedList = outputUserData.entrySet().stream()
                        .sorted(Map.Entry.<Recipe, Double>comparingByValue().reversed())
                        .skip((long) page * limit)
                        .limit(limit)
                        .map(Map.Entry::getKey)
                        .toList();
                recipeDtos = mapper.map(sortedList, new TypeToken<List<RecipeDto>>() {
                }.getType());
            } else {
                recipeDtos = mapper.map(recipeRepository.findTopRecipesWithLimitAndOffset(limit, page), new TypeToken<List<RecipeDto>>() {
                }.getType());
            }
        } else {
            recipeDtos = mapper.map(recipeRepository.findTopRecipesWithLimitAndOffset(limit, page), new TypeToken<List<RecipeDto>>() {
            }.getType());
        }

        List<RecipeDto> recipes = mapper.map(recipeRepository.findRandomWithLimit(limit - recipeDtos.size()), new TypeToken<List<RecipeDto>>() {
        }.getType());
        recipeDtos.addAll(recipes);
        return recipeDtos;
    }


}

