/*
 * Copyright (c) 2019 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.text;

import com.simiacryptus.ref.wrappers.RefString;
import com.simiacryptus.text.gpt2.GPT2Codec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Text generator.
 */
public class TextGenerator {
  /**
   * The constant logger.
   */
  protected static final Logger logger = LoggerFactory.getLogger(TextGenerator.class);
  /**
   * The Vocabulary size.
   */
  protected final int vocabularySize;
  /**
   * The Temperature
   */
  protected float temperature = .7f;
  /**
   * The Codec.
   */
  protected final GPT2Codec codec;
  /**
   * The Verbose.
   */
  protected boolean verbose = false;
  /**
   * The Choices to log.
   */
  protected int choicesToLog = 10;
  /**
   * The Codes.
   */
  @Nonnull
  List<Integer> codes = new ArrayList<>();
  List<Integer> output = new ArrayList<>();
  /**
   * The Next selections.
   */
  @Nullable
  float[] nextSelections;
  private LanguageCodeModel model;

  /**
   * Instantiates a new Text generator.
   *
   * @param vocabularySize the vocabulary size
   * @param model          the model
   * @param codec          the codec
   */
  public TextGenerator(int vocabularySize, LanguageCodeModel model, GPT2Codec codec) {
    this.setModel(model);
    this.vocabularySize = vocabularySize;
    this.codec = codec;
  }

  /**
   * Gets choices to log.
   *
   * @return the choices to log
   */
  public int getChoicesToLog() {
    return choicesToLog;
  }

  /**
   * Sets choices to log.
   *
   * @param choicesToLog the choices to log
   * @return the choices to log
   */
  @Nonnull
  public TextGenerator setChoicesToLog(int choicesToLog) {
    this.choicesToLog = choicesToLog;
    return this;
  }

  /**
   * Gets model.
   *
   * @return the model
   */
  public LanguageCodeModel getModel() {
    return model;
  }

  /**
   * Sets model.
   *
   * @param model the model
   * @return the model
   */
  @Nonnull
  public TextGenerator setModel(LanguageCodeModel model) {
    if (this.model == model) return this;
    if (null != this.model) this.model.clear();
    this.model = model;
    return this;
  }

  /**
   * Gets text.
   *
   * @return the text
   */
  public String getText() {
    String returnText = codec.decode(output.toArray(new Integer[]{}));
    //After we get our output text, clear the output array
    output.clear();
    return returnText;
  }

  /**
   * Gets vocabulary size.
   *
   * @return the vocabulary size
   */
  public int getVocabularySize() {
    return vocabularySize;
  }

  /**
   * Is verbose boolean.
   *
   * @return the boolean
   */
  public boolean isVerbose() {
    return verbose;
  }

  /**
   * Sets verbose.
   *
   * @param verbose the verbose
   * @return the verbose
   */
  @Nonnull
  public TextGenerator setVerbose(boolean verbose) {
    this.verbose = verbose;
    return this;
  }

  /**
   * Sorted indices int [ ].
   *
   * @param chosen the chosen
   * @param limit  the limit
   * @return the int [ ]
   */
  public static int[] sortedIndices(@Nonnull float[] chosen, int limit) {
    return IntStream.range(0, chosen.length)
        .mapToObj(x -> x)
        .sorted(Comparator.comparing(c -> -chosen[c]))
        .limit(limit)
        .mapToInt(x -> x)
        .toArray();
  }

  /**
   * Copy text generator.
   *
   * @return the text generator
   */
  @Nonnull
  public TextGenerator copy() {
    TextGenerator copy = new TextGenerator(vocabularySize, getModel().copy(), codec);
    copy.codes.addAll(this.codes);
    copy.verbose = this.verbose;
    copy.choicesToLog = this.choicesToLog;
    copy.nextSelections = null == this.nextSelections ? null : Arrays.copyOf(this.nextSelections, this.nextSelections.length);
    return copy;
  }

  /**
   * Generate text string.
   *
   * @param terminator the terminator
   * @return the string
   */
  @Nonnull
  public String generateText(@Nonnull Predicate<String> terminator) {
    return generateText(terminator, null);
  }

  /**
   * Generate text string.
   *
   * @param numberOfWords the number of words
   * @return the string
   */
  @Nonnull
  public String generateText(int numberOfWords) {
    return generateText(numberOfWords, null);
  }

  /**
   * Generate text string.
   *
   * @param terminator the terminator
   * @param prefix     the prefix
   * @return the string
   */
  @Nonnull
  public String generateText(@Nonnull Predicate<String> terminator, String prefix) {
    feed(prefix);
    generate(terminator);
    return getText();
  }

  /**
   * Generate text string and return result.
   *
   * @param numberOfTokens the number of tokens
   * @param prefix         the prefix
   * @return the string
   */
  @Nonnull
  //TODO: make wording consitant
  public String generateText(int numberOfTokens, String prefix) {
    feed(prefix);
    generate(numberOfTokens);
    return getText();
  }

  /**
   * Generate string.
   *
   * @param fn the fn
   * @return the string
   */
  public String generate(@Nonnull Predicate<String> fn) { //TODO: Why do we return a string on this generate but not the other?
    init();
    ArrayList<Integer> theseCodes = new ArrayList<>();
    try {
      for (int wordIndex = 0; wordIndex == 0 || fn.test(codec.decode(theseCodes.toArray(new Integer[]{}))); wordIndex++) {
        assert nextSelections != null;
        int selected = select(nextSelections);
        if (isVerbose()) {
          if (wordIndex != 0) log(nextSelections, codec, getChoicesToLog());
          logger.info(RefString.format("Selected New Text: '%s'", codec.decode(selected)));
        }
        if (selected == getVocabularySize() - 1) break;
        codes.add(selected);
        theseCodes.add(selected);
        nextSelections = getModel().eval(selected);
      }
    } catch (Throwable e) {
      //logger.warn("Error generating text", e);
      throw new RuntimeException("Error generating text: " + codec.decode(theseCodes.toArray(new Integer[]{})), e);
    }
    //TODO: Why does this return a string and the other generate not?
    return codec.decode(theseCodes.toArray(new Integer[]{}));
  }

  /**
   * Generates text.
   *
   * @param numberOfWords the number of words
   */
  public void generate(int numberOfWords) {
    init(); //TODO: do we need to init everytime we generate?
    try {
      for (int wordIndex = 0; wordIndex < numberOfWords; wordIndex++) {
        assert nextSelections != null;
        int selected = select(nextSelections);
        if (isVerbose()) {
          if (wordIndex != 0) log(nextSelections, codec, getChoicesToLog());
          logger.info(RefString.format("Selected New Text: '%s'", codec.decode(selected)));
        }
        if (selected == getVocabularySize() - 1) break;
        codes.add(selected);
        output.add(selected);
        nextSelections = getModel().eval(selected);
      }
    } catch (Throwable e) {
      logger.warn("Error generating text", e);
    }
  }

  /**
   * Init text generator.
   *
   * @return the text generator
   */
  @Nonnull
  public TextGenerator init() {
    if (nextSelections == null) feed("");
    return this;
  }

  /**
   * Feed text into the model.
   *
   * @param text the text
   * @return the input text
   */
  public String feed(String text) {
    List<Integer> codeList = new ArrayList<>();
    //Add all the encoded text to the codelist
    codeList.addAll(codec.encode(text));
    //If we fed nothing into the system set <|endoftext|> so the model knows
    if (codeList.isEmpty()) codeList.add(getVocabularySize() - 1);
    for (Integer code : codeList) {
        //Commented out because it was doubling up the inputs
        //Not sure if this is an actual improvement however
//      codes.add(code);
//      output.add(code);
      nextSelections = getModel().eval(code);
      if (isVerbose()) {
        logger.info(RefString.format("Feed token: '%s'", codec.decode(code)));
        assert nextSelections != null;
        log(nextSelections, codec, getChoicesToLog());
      }
    }
    return text;
  }

  /**
   * Reset text generator.
   *
   * @return the text generator
   */
  @Nonnull
  public TextGenerator reset() {
    codes.clear();
    getModel().clear();
    return this;
  }
  
  /**
   * Selects a token based on temperature setting
   *
   * @param chosen the chosen
   * @return int topCandidate
   */
  protected int select(@Nonnull float[] chosen) {
    double originalFate = Math.random() * temperature;//TODO: find a better way to calculate temperature
    double fate = originalFate;
    int j = 0;
    int[] topCandidates = sortedIndices(chosen, chosen.length);
    while (j < topCandidates.length && fate > chosen[topCandidates[j]]) {
      int topCandidate = topCandidates[j++];
      fate -= chosen[topCandidate];
    }
    int topCandidate = topCandidates[j];
    logger.debug(RefString.format("Chose #%s with fate %s", topCandidate, originalFate));
    return topCandidate;
  }

  /**
   * Log.
   *
   * @param chosen the chosen token
   * @param codec  the codec
   * @param count  the count
   */
  protected void log(@Nonnull float[] chosen, @Nonnull GPT2Codec codec, int count) {
    Arrays.stream(sortedIndices(chosen, count))
        .forEach(candidate -> logger.info(RefString.format("\t#%d %.4f%% '%s'", candidate, chosen[candidate] * 100, codec.decode(candidate))));
  }
  
  /**
   * 
   * @return the current temperature as a float
   */
    public float getTemperature() {
        return temperature;
    }
/**
 * Set the temperature for the model
 * Higher numbers equate to more randomness.
 * 
 * @param temperature 
 */
    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }
}
