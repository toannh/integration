/*
 * Copyright (C) 2003-2010 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.forum.ext.impl;

import java.beans.PropertyChangeEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.faq.service.Answer;
import org.exoplatform.faq.service.Comment;
import org.exoplatform.faq.service.FAQNodeTypes;
import org.exoplatform.faq.service.FAQService;
import org.exoplatform.faq.service.Question;
import org.exoplatform.faq.service.Utils;
import org.exoplatform.faq.service.impl.AnswerEventListener;
import org.exoplatform.forum.common.CommonUtils;
import org.exoplatform.forum.common.UserHelper;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.processor.I18NActivityUtils;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * @author <a href="mailto:patrice.lamarque@exoplatform.com">Patrice
 *         Lamarque</a>
 * @version $Revision$
 */
public class AnswersSpaceActivityPublisher extends AnswerEventListener {

  public static final String SPACE_APP_ID       = "ks-answer:spaces";
  public static final String ANSWER_APP_ID      = "answer:spaces";
  public static final String QUESTION_ID        = "Id";
  public static final String LINK_KEY           = "Link";
  public static final String LANGUAGE_KEY       = "Language";
  public static final String QUESTION_RATING    = "QuestionRating";
  public static final String NUMBER_OF_ANSWERS  = "NumberOfAnswers";
  public static final String NUMBER_OF_COMMENTS = "NumberOfComments";
  public static final String SPACE_GROUP_ID     = "SpaceGroupId";
  
  public static final String QUESTION_POINT     = "QuestionPoint";
  
  private final static Log LOG = ExoLogger.getExoLogger(AnswersSpaceActivityPublisher.class);
  
  private Identity getSpaceIdentity(String categoryId) {
    String spaceGroupId = ActivityUtils.getSpaceGroupId(categoryId);
    if ("".equals(spaceGroupId))
      return null;
    IdentityManager identityM = (IdentityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(IdentityManager.class);
    SpaceService spaceService  = (SpaceService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(SpaceService.class);
    Space space = spaceService.getSpaceByGroupId(spaceGroupId);
    if (space != null)
      return identityM.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
    else return null;
  }
  
  private ExoSocialActivity newActivity(Identity author, String title, String body, Map<String, String> templateParams) {
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle(CommonUtils.decodeSpecialCharToHTMLnumber(title));
    activity.setTitleId("add-question");
    activity.setBody(body);
    activity.setType(SPACE_APP_ID);
    activity.setTemplateParams(templateParams);
    activity.setUserId(author.getId());
    return activity;
  }
  
  private Map<String, String> updateTemplateParams(Map<String, String> templateParams, String questionId,
                                                   String questionRate, String nbAnswers, String nbComments,
                                                   String language, String link, int questionPoint) {
    templateParams.put(QUESTION_RATING, questionRate);
    templateParams.put(NUMBER_OF_ANSWERS, nbAnswers);
    templateParams.put(NUMBER_OF_COMMENTS, nbComments);
    templateParams.put(LINK_KEY, link);
    templateParams.put(LANGUAGE_KEY, language);
    templateParams.put(QUESTION_ID, questionId);
    templateParams.put(QUESTION_POINT, ""+ questionPoint);
    return templateParams;
  }
  
  private ExoSocialActivity createCommentForAnswer(Identity userIdentity, Answer answer) {
    ExoSocialActivityImpl comment = new ExoSocialActivityImpl();
    StringBuilder commentTitle = new StringBuilder();
    comment.setUserId(userIdentity.getId());
    comment.setType(ANSWER_APP_ID);
    
    //Build the message corresponding with an event
    String prefix = "";
    for (PropertyChangeEvent pce : answer.getChangeEvent()) {
      commentTitle.append(prefix);
      prefix="\n";
      commentTitle.append(getAnswerMessage(pce, answer, comment));
    }
    comment.setTitle(commentTitle.toString());
    return comment;
  }
  
  private ExoSocialActivity createCommentWhenUpdateQuestion(Identity userIdentity, Question question) {
    ExoSocialActivityImpl comment = new ExoSocialActivityImpl();
    comment.setType(ANSWER_APP_ID);
    comment.setUserId(userIdentity.getId());
    StringBuilder commentTitle = new StringBuilder();
    String prefix = "";
    for (PropertyChangeEvent pce : question.getChangeEvent()) {
      commentTitle.append(prefix);
      prefix="\n";
      commentTitle.append(getQuestionMessage(pce, question, comment));
    }
    comment.setTitle(commentTitle.toString());
    return comment;
  }
  
  private void updateActivity(ExoSocialActivity activity, Question question) {
    Map<String, String> activityTemplateParams = updateTemplateParams(new HashMap<String, String>(), question.getId(),
                                                                      ActivityUtils.getQuestionRate(question),
                                                                      ActivityUtils.getNbOfAnswers(question),
                                                                      ActivityUtils.getNbOfComments(question),
                                                                      question.getLanguage(), question.getLink(),
                                                                      Utils.getQuestionPoint(question));
    activity.setTemplateParams(activityTemplateParams);
    activity.setBody(null);
    activity.setTitle(null);
  }
  
  @Override
  public void moveQuestions(List<String> questions, String catId) {
    ActivityManager activityM = CommonsUtils.getService(ActivityManager.class);
    FAQService faqS = CommonsUtils.getService(FAQService.class);
    IdentityManager identityM = CommonsUtils.getService(IdentityManager.class);
    for (String questionId : questions) {
      try {
        Question question = faqS.getQuestionById(questionId);
        String activityId = faqS.getActivityIdForQuestion(question.getPath());
        Identity streamOwner = null;
        Map<String, String> templateParams = updateTemplateParams(new HashMap<String, String>(), question.getId(),
                ActivityUtils.getQuestionRate(question),
                ActivityUtils.getNbOfAnswers(question),
                ActivityUtils.getNbOfComments(question),
                question.getLanguage(), question.getLink(),
                Utils.getQuestionPoint(question));
        String questionDetail = ActivityUtils.processContent(question.getDetail());
        Identity spaceIdentity = getSpaceIdentity(catId);
        if (spaceIdentity != null) {
          streamOwner = spaceIdentity;
          templateParams.put(SPACE_GROUP_ID, ActivityUtils.getSpaceGroupId(catId));
        }
        if (activityId != null) {
          ExoSocialActivity oldActivity = activityM.getActivity(activityId);
          activityM.deleteActivity(oldActivity);
          Identity userIdentity = identityM.getOrCreateIdentity(OrganizationIdentityProvider.NAME, question.getAuthor(), false);
          ExoSocialActivity activity = newActivity(userIdentity, question.getQuestion(), questionDetail, templateParams);
          streamOwner = streamOwner != null ? streamOwner : userIdentity;
          activityM.saveActivityNoReturn(streamOwner, activity);
          faqS.saveActivityIdForQuestion(questionId, activity.getId());
          for (Answer answer : question.getAnswers()) {
            ExoSocialActivity comment = createCommentForAnswer(userIdentity, answer);
            String answerContent = ActivityUtils.processContent(answer.getResponses());
            comment.setTitle("Answer has been submitted: " + answerContent);
            I18NActivityUtils.addResourceKey(comment, "answer-add", answerContent);
            updateActivity(activity, question);
            activityM.updateActivity(activity);
            updateCommentTemplateParms(comment, answer.getId());
            activityM.saveComment(activity, comment);
            faqS.saveActivityIdForAnswer(questionId, answer, comment.getId());
          }
          for (Comment cm : question.getComments()) {
            String message = ActivityUtils.processContent(cm.getComments());
            ExoSocialActivityImpl comment = new ExoSocialActivityImpl();
            Map<String, String> commentTemplateParams = new HashMap<String, String>();
            commentTemplateParams.put(LINK_KEY, cm.getId());
            comment.setTemplateParams(commentTemplateParams);
            comment.setTitle(message);
            comment.setUserId(userIdentity.getId());
            updateActivity(activity, question);
            activityM.updateActivity(activity);
            activityM.saveComment(activity, comment);
            faqS.saveActivityIdForComment(questionId, cm.getId(), question.getLanguage(), comment.getId());
          }
        }
      } catch (Exception e) {
        LOG.error("Failed to move questions " + e.getMessage());
      }
    }
  }
  
  @Override
  public void saveAnswer(String questionId, Answer answer, boolean isNew) {
    try {
      ExoContainer exoContainer = ExoContainerContext.getCurrentContainer();
      IdentityManager identityM = (IdentityManager) exoContainer.getComponentInstanceOfType(IdentityManager.class);
      ActivityManager activityM = (ActivityManager) exoContainer.getComponentInstanceOfType(ActivityManager.class);
      FAQService faqS = (FAQService) exoContainer.getComponentInstanceOfType(FAQService.class);
      Question question = faqS.getQuestionById(questionId);
      Identity userIdentity = identityM.getOrCreateIdentity(OrganizationIdentityProvider.NAME,answer.getResponseBy(),false);
      String activityId = faqS.getActivityIdForQuestion(questionId);
      
      //
      String answerContent = ActivityUtils.processContent(answer.getResponses());
      
      if (activityId != null) {
        try {
          ExoSocialActivity activity = activityM.getActivity(activityId);
          ExoSocialActivity comment = createCommentForAnswer(userIdentity, answer);
          
          if (!comment.getTitle().equals("")) { //Case update answer or promote comment to answer
            String promotedAnswer = "Comment "+answerContent+" has been promoted as an answer";
            if (promotedAnswer.equals(comment.getTitle())) {
              //promote a comment to an answer
              updateCommentTemplateParms(comment, answer.getId());
              activityM.saveComment(activity, comment);
              faqS.saveActivityIdForAnswer(questionId, answer, comment.getId());
              
              //update question activity content
              updateActivity(activity, question);
              activityM.updateActivity(activity);
            } else {
              //update answer
              activityM.saveComment(activity, comment);
              String answerActivityId = faqS.getActivityIdForAnswer(questionId, answer);
              faqS.saveActivityIdForAnswer(questionId, answer, answerActivityId+","+comment.getId());
            }
          } else {
            //Case submit new answer
            comment.setTitle("Answer has been submitted: " + answerContent);
            I18NActivityUtils.addResourceKey(comment, "answer-add", answerContent);
            
            updateActivity(activity, question);
            activityM.updateActivity(activity);
            
            updateCommentTemplateParms(comment, answer.getId());
            activityM.saveComment(activity, comment);
            
            faqS.saveActivityIdForAnswer(questionId, answer, comment.getId());
          }
          
        } catch (Exception e) {
          LOG.debug("Run in case of activity deleted and reupdate");
          activityId = null;
        }
      }
      if (activityId == null) {
        saveQuestion(question, false);
        String newActivityId = faqS.getActivityIdForQuestion(question.getId());
        ExoSocialActivity activity = activityM.getActivity(newActivityId);
        ExoSocialActivity comment = createCommentForAnswer(userIdentity, answer);
        if (comment.getTitle().equals("")) {
          comment.setTitle("Answer has been submitted: " + answerContent);
          I18NActivityUtils.addResourceKey(comment, "answer-add", answerContent);
          updateCommentTemplateParms(comment, answer.getId());
        }
        activityM.saveComment(activity, comment);
      }
    } catch (Exception e) { // FQAService
      LOG.error("Can not record Activity for space when post answer ", e);
    }
  }

  @Override
  public void saveComment(String questionId, Comment cm, String language) {
    try {
      ExoContainer exoContainer = ExoContainerContext.getCurrentContainer();
      IdentityManager identityM = (IdentityManager) exoContainer.getComponentInstanceOfType(IdentityManager.class);
      ActivityManager activityM = (ActivityManager) exoContainer.getComponentInstanceOfType(ActivityManager.class);
      FAQService faqS = (FAQService) exoContainer.getComponentInstanceOfType(FAQService.class);
      Question question = faqS.getQuestionById(questionId);
      String message = ActivityUtils.processContent(cm.getComments());
      Identity userIdentity = identityM.getOrCreateIdentity(OrganizationIdentityProvider.NAME, cm.getCommentBy(), false);
      String activityId = faqS.getActivityIdForQuestion(questionId);
      if (activityId != null) {
        try {
          ExoSocialActivity activity = activityM.getActivity(activityId);
          ExoSocialActivityImpl comment = new ExoSocialActivityImpl();
          String commentActivityId = faqS.getActivityIdForComment(questionId, cm.getId(), language);
          Map<String, String> commentTemplateParams = new HashMap<String, String>();
          commentTemplateParams.put(LINK_KEY, cm.getId());
          if (commentActivityId != null) { //try to update activity's comment
            ExoSocialActivityImpl oldComment = (ExoSocialActivityImpl) activityM.getActivity(commentActivityId);
            if (oldComment != null) {
              comment = oldComment;
              comment.setTitle(message);
              comment.setTitleId("update-comment");
              activityM.updateActivity(comment);
            } else {
              commentActivityId = null;
            }
          }
          if (commentActivityId == null) { //create new activity's comment
            comment.setTemplateParams(commentTemplateParams);
            comment.setTitle(message);
            comment.setTitleId("add-comment");
            comment.setUserId(userIdentity.getId());
            updateActivity(activity, question);
            activityM.updateActivity(activity);
            activityM.saveComment(activity, comment);
            faqS.saveActivityIdForComment(questionId, cm.getId(), language, comment.getId());
          }
        } catch (Exception e) {
          LOG.debug("Run in case of activity deleted and reupdate");
          activityId = null;
        }
      } 
      if (activityId == null) { //Create new activity for the question and add new comment
        saveQuestion(question, false);
        String newActivityId = faqS.getActivityIdForQuestion(questionId);
        ExoSocialActivity activity = activityM.getActivity(newActivityId);
        ExoSocialActivity comment = new ExoSocialActivityImpl();
        comment.setUserId(userIdentity.getId());
        Map<String, String> commentTemplateParams = new HashMap<String, String>();
        commentTemplateParams.put(LINK_KEY, cm.getId());
        comment.setTitle(message);
        comment.setTemplateParams(commentTemplateParams);
        activityM.saveComment(activity, comment);
      }
    } catch (Exception e) { //FQAService      
      LOG.error("Can not record Activity for space when post comment ", e);
    }

  }

  @Override
  public void saveQuestion(Question question, boolean isNew) {
    try {
      ExoContainer exoContainer = ExoContainerContext.getCurrentContainer();
      IdentityManager identityM = (IdentityManager) exoContainer.getComponentInstanceOfType(IdentityManager.class);
      ActivityManager activityM = (ActivityManager) exoContainer.getComponentInstanceOfType(ActivityManager.class);
      FAQService faqS = (FAQService) exoContainer.getComponentInstanceOfType(FAQService.class);
      Identity userIdentity = identityM.getOrCreateIdentity(OrganizationIdentityProvider.NAME,question.getAuthor(),false);
      Map<String, String> templateParams = updateTemplateParams(new HashMap<String, String>(), question.getId(),
                                                                ActivityUtils.getQuestionRate(question),
                                                                ActivityUtils.getNbOfAnswers(question),
                                                                ActivityUtils.getNbOfComments(question),
                                                                question.getLanguage(), question.getLink(),
                                                                Utils.getQuestionPoint(question));
      String activityId = faqS.getActivityIdForQuestion(question.getId());
      
      String questionDetail = ActivityUtils.processContent(question.getDetail());
      //in case deleted activity, if isUpdate, we will re-create new activity and add a comment associated
      boolean isUpdate = false;
      // UserHelper.checkValueUser(values)
      if (activityId != null) {
        isUpdate = true;
        try {
          ExoSocialActivity activity = activityM.getActivity(activityId);
          if (UserHelper.getUserByUserId(question.getAuthor()) == null) {
            userIdentity = identityM.getIdentity(activity.getPosterId(), false);
          }
          activity.setTitle(CommonUtils.decodeSpecialCharToHTMLnumber(question.getQuestion()));
          activity.setBody(questionDetail);
          activity.setTemplateParams(templateParams);
          activityM.updateActivity(activity);

          ExoSocialActivity comment = createCommentWhenUpdateQuestion(userIdentity, question);
          if (!"".equals(comment.getTitle())) {
            activityM.saveComment(activity, comment);
          }
        } catch (Exception e) {
          LOG.debug("Run in case of activity deleted and reupdate");
          activityId = null;
        }
      }
      if (activityId == null) {
        Identity streamOwner = null;
        String catId = (String) faqS.readQuestionProperty(question.getId(), FAQNodeTypes.EXO_CATEGORY_ID, String.class);
        Identity spaceIdentity = getSpaceIdentity(catId);
        if (spaceIdentity != null) {
          // publish the activity in the space stream.
          streamOwner = spaceIdentity;
          templateParams.put(SPACE_GROUP_ID, ActivityUtils.getSpaceGroupId(catId));
        }
        List<String> categoryIds = faqS.getCategoryPath(catId);
        Collections.reverse(categoryIds);
        if (streamOwner == null) {
          streamOwner = userIdentity;
        }
        ExoSocialActivity activity = newActivity(userIdentity, question.getQuestion(), questionDetail, templateParams);
        activityM.saveActivityNoReturn(streamOwner, activity);
        faqS.saveActivityIdForQuestion(question.getId(), activity.getId());

        if (isUpdate) {
          ExoSocialActivity comment = createCommentWhenUpdateQuestion(userIdentity, question);
          if (!"".equals(comment.getTitle())) {
            activityM.saveComment(activity, comment);
          }
        }
      }
    } catch (Exception e) { // FQAService
      LOG.error("Can not record Activity for space when add new question ", e);
    }
  }

  @Override
  public void saveAnswer(String questionId, Answer[] answers, boolean isNew) {
    try {
      Class.forName("org.exoplatform.social.core.manager.IdentityManager");
      if (answers != null) {
        for (Answer a : answers) {
          saveAnswer(questionId, a, isNew);
        }
      }
      
    } catch (ClassNotFoundException e) {
      if (LOG.isDebugEnabled())
        LOG.debug("Please check the integrated project does the social deploy? " + e.getMessage());
    } 
    //catch other type of exception in saveAnswer(String questionId, Answer answers, boolean isNew)
  }
  
  public void voteQuestion(String questionId) {
    try {
      ExoContainer exoContainer = ExoContainerContext.getCurrentContainer();
      FAQService faqS = (FAQService) exoContainer.getComponentInstanceOfType(FAQService.class);
      Question question = faqS.getQuestionById(questionId);
      
      //No event is created because old and new values are equal but we must update the activity's content
      question.setEditedQuestionRating(question.getMarkVote());
      saveQuestion(question, false);
    } catch (Exception e) {
      LOG.debug("Fail to vote question "+e.getMessage());
    }
  }
  
  public void unVoteQuestion(String questionId) {
    try {
      ExoContainer exoContainer = ExoContainerContext.getCurrentContainer();
      FAQService faqS = (FAQService) exoContainer.getComponentInstanceOfType(FAQService.class);
      Question question = faqS.getQuestionById(questionId);
      
      //No event is created because old and new values are equal but we must update the activity's content
      question.setEditedQuestionRating(question.getMarkVote());
      saveQuestion(question, false);
    } catch (Exception e) {
      LOG.debug("Fail to unvote question " + e.getMessage());
    }
  }
  
  public void removeQuestion(String questionActivityId) {
    try {
      ExoContainer exoContainer = ExoContainerContext.getCurrentContainer();
      ActivityManager activityM = (ActivityManager) exoContainer.getComponentInstanceOfType(ActivityManager.class);
      ExoSocialActivity activity = activityM.getActivity(questionActivityId);
      activityM.deleteActivity(activity);
    } catch (Exception e) {
      LOG.debug("Fail to remove activity when remove question "+e.getMessage());
    }
  }
  
  public void removeAnswer(String questionPath, String answerActivityId) {
    try {
      ExoContainer exoContainer = ExoContainerContext.getCurrentContainer();
      FAQService faqS = (FAQService) exoContainer.getComponentInstanceOfType(FAQService.class);
      String questionActivityId = faqS.getActivityIdForQuestion(questionPath);
      ActivityManager activityM = (ActivityManager) exoContainer.getComponentInstanceOfType(ActivityManager.class);
      for (String id : answerActivityId.split(",")) {
        ExoSocialActivity activity = activityM.getActivity(id);
        if (activity != null) {
          activityM.deleteComment(questionActivityId, id);
        }
      }
      refreshActivity(questionPath, questionActivityId);
    } catch (Exception e) {
      LOG.debug("Fail to remove comment when remove question's answer "+e.getMessage());
    }
  }
  
  public void removeComment(String questionActivityId, String commentActivityId, String questionPath) {
    try {
      ExoContainer exoContainer = ExoContainerContext.getCurrentContainer();
      ActivityManager activityM = (ActivityManager) exoContainer.getComponentInstanceOfType(ActivityManager.class);
      activityM.deleteComment(questionActivityId, commentActivityId);
      refreshActivity(questionPath, questionActivityId);
    } catch (Exception e) {
      LOG.debug("Fail to remove comment when remove question's comment "+e.getMessage());
    }
  }
  
  private String getQuestionMessage(PropertyChangeEvent e, Question question, ExoSocialActivity comment) {
    String questionName = CommonUtils.decodeSpecialCharToHTMLnumber(question.getQuestion());
    String questionDetail = ActivityUtils.processContent(question.getDetail());
    if ("questionName".equals(e.getPropertyName())) {
      I18NActivityUtils.addResourceKey(comment, "question-update-title", questionName);
      return "Title has been updated to: " + questionName;
    } else if ("questionDetail".equals(e.getPropertyName())) {
      I18NActivityUtils.addResourceKey(comment, "question-update-detail", questionDetail);
      return "Details has been edited to: " + questionDetail;
    } else if ("questionActivated".equals(e.getPropertyName())) {
      if (question.isActivated()) {
        I18NActivityUtils.addResourceKey(comment, "question-activated", null);
        return "Question has been activated.";
      } else {
        I18NActivityUtils.addResourceKey(comment, "question-unactivated", null);
        return "Question has been unactivated.";
      }
    } else if ("questionAttachment".equals(e.getPropertyName())) {
      I18NActivityUtils.addResourceKey(comment, "question-add-attachment", null);
      return "Attachment(s) has been added.";
    } else { //case of add new language
      int length = question.getMultiLanguages().length;
      I18NActivityUtils.addResourceKey(comment, "question-add-language", question.getMultiLanguages()[length-1].getLanguage());
      return "Question has been added in "+question.getMultiLanguages()[length-1].getLanguage();
    }
  }
  
  private String getAnswerMessage(PropertyChangeEvent e, Answer answer, ExoSocialActivity comment) {
    String answerContent = ActivityUtils.processContent(answer.getResponses());
    if ("answerEdit".equals(e.getPropertyName())) {
      I18NActivityUtils.addResourceKey(comment, "answer-update-content", answerContent);
      return "Answer has been edited to: " + answerContent;
    } else if ("answerPromoted".equals(e.getPropertyName())) {
      I18NActivityUtils.addResourceKey(comment, "answer-promoted", answerContent);
      return "Comment "+answerContent + " has been promoted as an answer";
    } else if ("answerActivated".equals(e.getPropertyName())) {
      if (answer.getActivateAnswers()) {
        I18NActivityUtils.addResourceKey(comment, "answer-activated", answerContent);
        return "Answer has been activated: " + answerContent + ".";
      } else {
        I18NActivityUtils.addResourceKey(comment, "answer-unactivated", answerContent);
        return "Answer has been unactivated: " + answerContent + ".";
      }
    } else  {
      if (answer.getApprovedAnswers()) {
        I18NActivityUtils.addResourceKey(comment, "answer-approved", answerContent);
        return "Answer has been approved: "+answerContent+".";
      } else {
        I18NActivityUtils.addResourceKey(comment, "answer-disapproved", answerContent);
        return "Answer has been disapproved: " + answerContent + ".";
      }
    }
  }
  
  private void refreshActivity(String questionId, String questionActivityId) {
    try {
      ExoContainer exoContainer = ExoContainerContext.getCurrentContainer();
      FAQService faqS = (FAQService) exoContainer.getComponentInstanceOfType(FAQService.class);
      Question question = faqS.getQuestionById(questionId);
      ActivityManager activityM = (ActivityManager) exoContainer.getComponentInstanceOfType(ActivityManager.class);
      ExoSocialActivity activity = activityM.getActivity(questionActivityId);
      updateActivity(activity, question);
      activityM.updateActivity(activity);
    } catch (Exception e) {
      LOG.debug("Fail to refresh activity " + e.getMessage());
    }
  }
  
  private void updateCommentTemplateParms(ExoSocialActivity comment, String link) {
    Map<String, String> commentTemplateParams = comment.getTemplateParams();
    if (commentTemplateParams == null) 
      commentTemplateParams = new HashMap<String, String>();
    commentTemplateParams.put(LINK_KEY, link);
    comment.setTemplateParams(commentTemplateParams);
  }
  
}
