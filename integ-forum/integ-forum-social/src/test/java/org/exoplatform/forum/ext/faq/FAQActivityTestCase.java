/*
 * Copyright (C) 2003-2013 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.forum.ext.faq;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.faq.service.Answer;
import org.exoplatform.faq.service.Category;
import org.exoplatform.faq.service.CategoryInfo;
import org.exoplatform.faq.service.Comment;
import org.exoplatform.faq.service.FAQEventQuery;
import org.exoplatform.faq.service.FAQSetting;
import org.exoplatform.faq.service.FileAttachment;
import org.exoplatform.faq.service.JCRPageList;
import org.exoplatform.faq.service.ObjectSearchResult;
import org.exoplatform.faq.service.Question;
import org.exoplatform.faq.service.QuestionLanguage;
import org.exoplatform.faq.service.Utils;
import org.exoplatform.faq.service.impl.MultiLanguages;
import org.exoplatform.forum.common.NotifyInfo;
import org.exoplatform.forum.ext.impl.AnswersSpaceActivityPublisher;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;

@SuppressWarnings("unused")
public class FAQActivityTestCase extends FAQServiceBaseTestCase {

  private List<FileAttachment> listAttachments = new ArrayList<FileAttachment>();
  
  AnswersSpaceActivityPublisher listener = new AnswersSpaceActivityPublisher();

  public FAQActivityTestCase() throws Exception {
    super();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    faqService_.addListenerPlugin(listener);
  }
  
  @Override
  public void tearDown() throws Exception {
    faqService_.removeListenerPlugin(listener);
    super.tearDown();
  }

  public void testFAQService() throws Exception {
    assertNotNull(faqService_);
  }
  
  public void testQuestionTitle() throws Exception {
    Category cate = createCategory("Category to test question", 6);
    String categoryId = Utils.CATEGORY_HOME + "/" + cate.getId();
    faqService_.saveCategory(Utils.CATEGORY_HOME, cate, true);
    Question question = createQuestion(categoryId);
    
    //save new question
    faqService_.saveQuestion(question, true, faqSetting_);
    
    //an activity is created
    String activityId = faqService_.getActivityIdForQuestion(question.getCategoryPath() + "/" + question.getId());
    ExoSocialActivity activity = getManager().getActivity(activityId);
    assertEquals(question.getQuestion(), activity.getTitle());
    List<ExoSocialActivity> comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(0, comments.size());
    
    //update question's title
    question = faqService_.getQuestionById(question.getId());
    question.setQuestion("&-*()");
    faqService_.saveQuestion(question, false, faqSetting_);
    activity = getManager().getActivity(activityId);
    assertEquals("&-*()", activity.getTitle());
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals("Title has been updated to: &-*()", comments.get(0).getTitle());
    
    //update question's title
    question.setQuestion("&-*() / --- == coucou #@");
    faqService_.saveQuestion(question, false, faqSetting_);
    activity = getManager().getActivity(activityId);
    assertEquals("&-*() / --- == coucou #@", activity.getTitle());
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals("Title has been updated to: &-*() / --- == coucou #@", comments.get(1).getTitle());
    
    //update question with bbcode
    question.setDetail("[url=https://jira.exoplatform.org/browse/INTEG-166]&-*() / --- == coucou #@[/url]");
    faqService_.saveQuestion(question, false, faqSetting_);
    activity = getManager().getActivity(activityId);
    assertEquals("&-*() / --- == coucou #@", activity.getTitle());
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals("Details has been edited to: &amp;-*() / --- == coucou #@", comments.get(2).getTitle());

    question.setDetail("<strong>&-*() /</strong> and [B]@$#$%[/B]");
    faqService_.saveQuestion(question, false, faqSetting_);
    activity = getManager().getActivity(activityId);
    assertEquals("&-*() / --- == coucou #@", activity.getTitle());
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals("Details has been edited to: &amp;-*() / and @$#$%", comments.get(3).getTitle());
    
    String questionPath = question.getCategoryPath()+ "/" + Utils.QUESTION_HOME + "/" + question.getId();
    //delete question will delete the activity
    faqService_.removeQuestion(questionPath);
    activity = getManager().getActivity(activityId);
    assertNull(activity);
  }

  public void testCreateQuestion() throws Exception {
    Category cate = createCategory("Category to test question", 6);
    String categoryId = Utils.CATEGORY_HOME + "/" + cate.getId();
    faqService_.saveCategory(Utils.CATEGORY_HOME, cate, true);
    Question question = createQuestion(categoryId);
    
    //save new question
    faqService_.saveQuestion(question, true, faqSetting_);
    
    //an activity is created
    String activityId = faqService_.getActivityIdForQuestion(question.getCategoryPath() + "/" + question.getId());
    ExoSocialActivity activity = getManager().getActivity(activityId);
    assertEquals(question.getQuestion(), activity.getTitle());
    String questionPath = question.getCategoryPath()+ "/" + Utils.QUESTION_HOME + "/" + question.getId();
    
    //delete question will delete the activity
    faqService_.removeQuestion(questionPath);
    activity = getManager().getActivity(activityId);
    assertNull(activity);
  }
  
  public void testRemoveCategory() throws Exception {
    Category cate = createCategory("Category will be removed", 7);
    String categoryId = Utils.CATEGORY_HOME + "/" + cate.getId();
    faqService_.saveCategory(Utils.CATEGORY_HOME, cate, true);
    Question question = createQuestion(categoryId);
    
    //save new question
    faqService_.saveQuestion(question, true, faqSetting_);
    
    //an activity is created
    String activityId = faqService_.getActivityIdForQuestion(question.getCategoryPath() + "/" + question.getId());
    ExoSocialActivity activity1 = getManager().getActivity(activityId);
    assertEquals(question.getQuestion(), activity1.getTitle());
    
    //sub category in level 2
    Category cate2 = createCategory("Sub-Category will be removed", 8);
    faqService_.saveCategory(cate.getPath(), cate2, true);
    String categoryId2 = cate2.getPath();
    
    question = createQuestion(categoryId2);
    faqService_.saveQuestion(question, true, faqSetting_);
    
    String activityId2 = faqService_.getActivityIdForQuestion(question.getCategoryPath() + "/" + question.getId());
    ExoSocialActivity activity2 = getManager().getActivity(activityId2);
    assertEquals(question.getQuestion(), activity2.getTitle());
    
    //sub category in level 3
    Category cate3 = createCategory("Sub-sub-Category will be removed", 9);
    faqService_.saveCategory(cate2.getPath(), cate3, true);
    String categoryId3 = cate3.getPath();
    
    question = createQuestion(categoryId3);
    faqService_.saveQuestion(question, true, faqSetting_);
    
    String activityId3 = faqService_.getActivityIdForQuestion(question.getCategoryPath() + "/" + question.getId());
    ExoSocialActivity activity3 = getManager().getActivity(activityId3);
    assertEquals(question.getQuestion(), activity3.getTitle());
    
    //remove category of question will delete all its activities and sub categories's 
    faqService_.removeCategory(categoryId);
    
    //check the activity
    activity1 = getManager().getActivity(activityId);
    assertNull(activity1);
    
    activity2 = getManager().getActivity(activityId2);
    assertNull(activity2);
    
    activity3 = getManager().getActivity(activityId3);
    assertNull(activity3);
  }
  
  public void testUpdateQuestion() throws Exception {
    Category cate = createCategory("Category to test question", 6);
    String categoryId = Utils.CATEGORY_HOME + "/" + cate.getId();
    faqService_.saveCategory(Utils.CATEGORY_HOME, cate, true);
    Question question = createQuestion(categoryId);
    
    //save new question
    faqService_.saveQuestion(question, true, faqSetting_);
    
    //an activity is created
    String activityId = faqService_.getActivityIdForQuestion(question.getCategoryPath() + "/" + question.getId());
    ExoSocialActivity activity = getManager().getActivity(activityId);
    assertEquals(question.getQuestion(), activity.getTitle());
    String questionPath = question.getCategoryPath()+ "/" + Utils.QUESTION_HOME + "/" + question.getId();
    
    //update question's title
    question = faqService_.getQuestionById(question.getId());
    question.setQuestion("new question's title");
    faqService_.saveQuestion(question, false, faqSetting_);
    activity = getManager().getActivity(activityId);
    List<ExoSocialActivity> comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(1,comments.size());
    assertEquals("Title has been updated to: new question's title", comments.get(0).getTitle());
    
    //update question's detail
    question = faqService_.getQuestionById(question.getId());
    question.setDetail("new question's detail\n1\n2\n3\n4");
    faqService_.saveQuestion(question, false, faqSetting_);
    activity = getManager().getActivity(activityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(2,comments.size());
    assertEquals("Details has been edited to: new question's detail<br/>1<br/>2<br/>3...", comments.get(1).getTitle());
    
    //unactivate question
    question = faqService_.getQuestionById(question.getId());
    question.setActivated(false);
    faqService_.saveQuestion(question, false, faqSetting_);
    activity = getManager().getActivity(activityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(3,comments.size());
    assertEquals("Question has been unactivated.", comments.get(2).getTitle());
    
    //activate question
    question = faqService_.getQuestionById(question.getId());
    question.setActivated(true);
    faqService_.saveQuestion(question, false, faqSetting_);
    activity = getManager().getActivity(activityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(4,comments.size());
    assertEquals("Question has been activated.", comments.get(3).getTitle());
    
    //add a attachment
    question = faqService_.getQuestionById(question.getId());
    faqService_.saveUserAvatar(USER_ROOT, createUserAvatar("defaultAvatar.jpg"));
    FileAttachment file = faqService_.getUserAvatar(USER_ROOT);
    listAttachments.add(file);
    question.setAttachMent(listAttachments);
    faqService_.saveQuestion(question, false, faqSetting_);
    activity = getManager().getActivity(activityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(5,comments.size());
    assertEquals("Attachment(s) has been added.", comments.get(4).getTitle());
    
    //add new question's language
    question = faqService_.getQuestionById(question.getId());
    QuestionLanguage lang = createQuestionLanguage("French");
    question.setMultiLanguages(new QuestionLanguage[] { lang });
    faqService_.saveQuestion(question, false, faqSetting_);
    activity = getManager().getActivity(activityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(6,comments.size());
    assertEquals("Question has been added in French", comments.get(5).getTitle());
    
    //delete question will delete the activity
    faqService_.removeQuestion(questionPath);
    activity = getManager().getActivity(activityId);
    assertNull(activity);
  }
  
  public void testMoveQuestions() throws Exception {
    Category cate = createCategory("WatchedCategory", 6);
    String categoryId = Utils.CATEGORY_HOME + "/" + cate.getId();
    faqService_.saveCategory(Utils.CATEGORY_HOME, cate, true);
    Question question = createQuestion(categoryId);
    
    //save new question
    faqService_.saveQuestion(question, true, faqSetting_);
    
    //an activity is created
    String activityId = faqService_.getActivityIdForQuestion(question.getCategoryPath() + "/" + question.getId());
    ExoSocialActivity activity = getManager().getActivity(activityId);
    assertEquals(question.getQuestion(), activity.getTitle());
    String questionPath = question.getCategoryPath()+ "/" + Utils.QUESTION_HOME + "/" + question.getId();
    
    //
    Category destCate = createCategory("DestCategory", 8);
    faqService_.saveCategory(Utils.CATEGORY_HOME, destCate, true);
    List<String> questionIds = new ArrayList<String>();
    questionIds.add(questionPath);
    faqService_.moveQuestions(questionIds, Utils.CATEGORY_HOME + "/" + destCate.getId(), null, faqSetting_);

    activity = getManager().getActivity(activityId);
    assertNull(activity);
    
    String newActivityId = faqService_.getActivityIdForQuestion(destCate.getPath() + "/" + question.getId());
    activity = getManager().getActivity(newActivityId);
    assertNotNull(activity);
  }
  
  public void testAddUpdateAnswer() throws Exception {
    Category cate = createCategory("Category to test question", 6);
    String categoryId = Utils.CATEGORY_HOME + "/" + cate.getId();
    faqService_.saveCategory(Utils.CATEGORY_HOME, cate, true);
    Question question = createQuestion(categoryId);
    
    //save new question
    faqService_.saveQuestion(question, true, faqSetting_);
    
    //an activity is created
    String activityId = faqService_.getActivityIdForQuestion(question.getCategoryPath() + "/" + question.getId());
    ExoSocialActivity activity = getManager().getActivity(activityId);
    assertEquals(question.getQuestion(), activity.getTitle());
    String questionPath = question.getCategoryPath()+ "/" + Utils.QUESTION_HOME + "/" + question.getId();
    
    //create answer
    Answer answer = createAnswer("john", "Response of the previous question");
    question.setAnswers(new Answer[] { answer });
    faqService_.saveAnswer(questionPath, question.getAnswers());
    activity = getManager().getActivity(activityId);
    List<ExoSocialActivity> comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(1,comments.size());
    assertEquals("Answer has been submitted: Response of the previous question", comments.get(0).getTitle());
    
    //update answer's response
    answer = faqService_.getAnswerById(questionPath, answer.getId());
    answer.setLanguage("English");
    answer.setResponses("New response");
    question.setAnswers(new Answer[] { answer });
    faqService_.saveAnswer(questionPath, question.getAnswers());
    activity = getManager().getActivity(activityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(2,comments.size());
    assertEquals("Answer has been edited to: New response", comments.get(1).getTitle());
    
    //create new answer
    Answer answer2 = createAnswer("demo", "Response 2 of the previous question");
    question.setAnswers(new Answer[] { answer2 });
    faqService_.saveAnswer(questionPath, question.getAnswers());
    activity = getManager().getActivity(activityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(3,comments.size());
    assertEquals("Answer has been submitted: Response 2 of the previous question", comments.get(2).getTitle());
    
    //delete answer will delete the comment associated
    faqService_.deleteAnswerQuestionLang(questionPath, answer2.getId(), answer2.getLanguage());
    activity = getManager().getActivity(activityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(2,comments.size());
    
    //delete activity and add new answer, this will re-create an activity with a comment associated
    getManager().deleteActivity(activityId);
    activity = getManager().getActivity(activityId);
    assertNull(activity);
    Answer answer3 = createAnswer("demo", "Response 3 of the previous question");
    question.setAnswers(new Answer[] { answer3 });
    faqService_.saveAnswer(questionPath, question.getAnswers());
    String newActivityId = faqService_.getActivityIdForQuestion(question.getCategoryPath() + "/" + question.getId());
    activity = getManager().getActivity(newActivityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(1,comments.size());
    assertEquals("Answer has been submitted: Response 3 of the previous question", comments.get(0).getTitle());
    
    //delete question will delete the activity
    faqService_.removeQuestion(questionPath);
    activity = getManager().getActivity(activityId);
    assertNull(activity);
  }
  
  public void testAddUpdateComment() throws Exception {
    Category cate = createCategory("Category to test question", 6);
    String categoryId = Utils.CATEGORY_HOME + "/" + cate.getId();
    faqService_.saveCategory(Utils.CATEGORY_HOME, cate, true);
    Question question = createQuestion(categoryId);
    
    //save new question
    faqService_.saveQuestion(question, true, faqSetting_);
    
    //an activity is created
    String activityId = faqService_.getActivityIdForQuestion(question.getCategoryPath() + "/" + question.getId());
    ExoSocialActivity activity = getManager().getActivity(activityId);
    assertEquals(question.getQuestion(), activity.getTitle());
    String questionPath = question.getCategoryPath()+ "/" + Utils.QUESTION_HOME + "/" + question.getId();
    
    //create a comment
    Comment comment = createComment("demo", "Comment 1");
    question.setComments(new Comment[] { comment });
    faqService_.saveComment(questionPath, comment, "English");
    activity = getManager().getActivity(activityId);
    List<ExoSocialActivity> comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(1,comments.size());
    assertEquals(comment.getComments(), comments.get(0).getTitle());
    
    //update a comment will not add new comment on activity
    comment = faqService_.getCommentById(questionPath, comment.getId());
    assertNotNull(comment);
    comment.setComments("new Comment 1");
    question.setComments(new Comment[] { comment });
    faqService_.saveComment(questionPath, comment, "English");
    activity = getManager().getActivity(activityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(1,comments.size());
    assertEquals(comment.getComments(), comments.get(0).getTitle());
    
    //create new comment
    Comment newComment = createComment("demo", "Comment 2");
    question.setComments(new Comment[] { newComment });
    faqService_.saveComment(questionPath, newComment, "English");
    activity = getManager().getActivity(activityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(2,comments.size());
    assertEquals(newComment.getComments(), comments.get(1).getTitle());
    
    //delete a comment of question will delete the comment of activity
    faqService_.deleteCommentQuestionLang(questionPath, newComment.getId(), "English", false);
    activity = getManager().getActivity(activityId);
    comments = getManager().getCommentsWithListAccess(activity).loadAsList(0, 10);
    assertEquals(1,comments.size());
    
    //delete question will delete the activity
    faqService_.removeQuestion(questionPath);
    activity = getManager().getActivity(activityId);
    assertNull(activity);
  }

  private ActivityManager getManager() {
    return (ActivityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ActivityManager.class);
  }
  
  private FileAttachment createUserAvatar(String fileName) throws Exception {
    FileAttachment attachment = new FileAttachment();
    try {
      File file = new File("../integ-forum-social/src/test/resources/conf/portal/defaultAvatar.jpg");
      attachment.setName(fileName);
      InputStream is = new FileInputStream(file);
      attachment.setInputStream(is);
      attachment.setMimeType("image/jpg");
    } catch (Exception e) {
      LOG.error("Fail to create user avatar: ", e);
    }
    return attachment;
  }
  
}
