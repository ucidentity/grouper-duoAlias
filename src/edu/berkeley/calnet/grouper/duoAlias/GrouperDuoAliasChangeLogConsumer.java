/**
 * @author mchyzer
 * $Id$
 */
package edu.berkeley.calnet.grouper.duoAlias;

import java.util.List;
import java.util.Map;

import edu.berkeley.calnet.grouper.duoAlias.GrouperDuoAliasCommands;
import edu.berkeley.calnet.grouper.duoAlias.GrouperDuoAliasSet;
import edu.berkeley.calnet.grouper.duoAlias.GrouperDuoAliasUtils;
import org.apache.commons.lang3.StringUtils;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Subject;


/**
 *
 */
public class GrouperDuoAliasChangeLogConsumer extends ChangeLogConsumerBase {

  /**
   * 
   */
  public GrouperDuoAliasChangeLogConsumer() {
    //schedule with job in grouper-loader.properties
    //otherJob.duo.class = edu.internet2.middleware.grouperDuo.GrouperDuoFullRefresh
    //otherJob.duo.quartzCron = 0 0 5 * * ?
    //GrouperDuoDaemon.scheduleJobsOnce();
  }

  /**
   * @see edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase#processChangeLogEntries(java.util.List, edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata)
   */
  @Override
  public long processChangeLogEntries(List<ChangeLogEntry> changeLogEntryList,
      ChangeLogProcessorMetadata changeLogProcessorMetadata) {
    
    long currentId = -1;

    boolean startedGrouperSession = false;
    GrouperSession grouperSession = GrouperSession.staticGrouperSession(false);
    if (grouperSession == null) {
      grouperSession = GrouperSession.startRootSession();
      startedGrouperSession = true;
    } else {
      grouperSession = grouperSession.internal_getRootSession();
    }
    
    //try catch so we can track that we made some progress
    try {
      GrouperDuoAliasUtils.doAliasSetup();
      for (ChangeLogEntry changeLogEntry : changeLogEntryList) {
        currentId = changeLogEntry.getSequenceNumber();
        
        boolean isMembershipAdd = changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD);
        boolean isMembershipDelete = changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_DELETE);
        boolean isMembershipUpdate = changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_UPDATE);
        if (isMembershipAdd || isMembershipDelete || isMembershipUpdate) {
          String groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName);

          if (GrouperDuoAliasUtils.validGroupName(groupName)) {
            String sourceId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.sourceId);

              String subjectId = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
              
              String subjectAttributeForDuoUsername = GrouperDuoAliasUtils.configSubjectAttributeForDuoUsername();

              Group group = GroupFinder.findByName(grouperSession, groupName, false);
                
              String username = null;
              Subject subject = SubjectFinder.findByIdAndSource(subjectId, sourceId, false);
              
              if (StringUtils.equals("id", subjectAttributeForDuoUsername)) {
                username = subjectId;
              } else {
                
                if (subject != null) {
                  String attributeValue = subject.getAttributeValue(subjectAttributeForDuoUsername);
                  if (!StringUtils.isBlank(attributeValue)) {
                    username = attributeValue;
                  }                    
                }
              }

              String duoUserId = !StringUtils.isBlank(username) ? GrouperDuoAliasCommands.retrieveUserIdFromUsername(username) : null;
              
              //cant do anything if missing this
              if (!StringUtils.isBlank(duoUserId)) {
                // get the complete alias set
                GrouperDuoAliasSet aliasSet = GrouperDuoAliasUtils.getAliasValue(subject, groupName);
                boolean addDuoAlias = isMembershipAdd;
                
                //if update it could have unexpired
                if (isMembershipUpdate && group != null && subject != null && group.hasMember(subject)) {
                  addDuoAlias = true;
                }
                

                if (addDuoAlias) {
                  GrouperDuoAliasCommands.assignDuoUserAlias(duoUserId, aliasSet.getAliasName(), aliasSet.getAliasValue());
                } else {
                  GrouperDuoAliasCommands.assignDuoUserAlias(duoUserId, aliasSet.getAliasName(),"");
                }

              }
            }
          }
 
        //we successfully processed this record
      }
    } catch (Exception e) {
      changeLogProcessorMetadata.registerProblem(e, "Error processing record", currentId);
      //we made it to this -1
      return currentId-1;
    } finally {
      if (startedGrouperSession) {
        GrouperSession.stopQuietly(grouperSession);
      }
    }
    if (currentId == -1) {
      throw new RuntimeException("Couldnt process any records");
    }
 
    return currentId;

  }

}
