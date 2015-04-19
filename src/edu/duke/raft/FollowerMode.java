package edu.duke.raft;

import java.util.Timer;


public class FollowerMode extends RaftMode {
	private Timer mTimer;
	private int ELECTION_TIMEOUT;
	
	public void go () {
	    synchronized (mLock) {
		int term = mConfig.getCurrentTerm();
		System.out.println ("S" + 
				    mID + 
				    "." + 
				    term + 
				    ": switched to follower mode.");
		//set a time to handle timeout
		ELECTION_TIMEOUT =  (int)(((double)ELECTION_TIMEOUT_MAX-(double)ELECTION_TIMEOUT_MIN)*Math.random())+ELECTION_TIMEOUT_MIN; 
		//clear status
		mConfig.setCurrentTerm(term, 0);
		RaftResponses.setTerm(term);
		RaftResponses.clearVotes(term);
		RaftResponses.clearAppendResponses(term);
     
		mTimer = this.scheduleTimer(ELECTION_TIMEOUT,mID);
	    }
	}
    
  // @param candidate’s term
  // @param candidate requesting vote
  // @param index of candidate’s last log entry
  // @param term of candidate’s last log entry
  // @return 0, if server votes for candidate; otherwise, server's
  // current term
  public int requestVote (int candidateTerm,
			  int candidateID,
			  int lastLogIndex,
			  int lastLogTerm) {
    synchronized (mLock) {
	mTimer.cancel();
	int term = mConfig.getCurrentTerm ();
	int vote = term;
	int voteFor = mConfig.getVotedFor();
	int lastIndex = mLog.getLastIndex();
	int lastTerm = mLog.getLastTerm();
	//System.out.println("S"+mID+" in requestVote candidateTerm: "+candidateTerm+" ID:"+candidateID);
	if (candidateTerm<=term)  
	    {
		mTimer = this.scheduleTimer(ELECTION_TIMEOUT,mID); 
		System.out.println("server "+mID+"does not vote to server "+candidateID);
		return vote;
	    } 
	//candidateTerm>term
	//already voted in current term
	System.out.println("candidateTerm>term");
	if (lastLogTerm>lastTerm || (lastLogTerm == lastTerm && lastLogIndex>=lastIndex))
	    {
		//say yes, update local term
		System.out.println("server "+mID+" vote to server "+candidateID);
		vote = 0;
	    }
	mConfig.setCurrentTerm(candidateTerm, candidateID); //set current term and voted for
	//default say no 
	System.out.println("server "+mID+"does not vote to server "+candidateID);
	mTimer = this.scheduleTimer(ELECTION_TIMEOUT,mID); 
	return vote;
    }
  }
  

  // @param leader’s term
  // @param current leader
  // @param index of log entry before entries to append
  // @param term of log entry before entries to append
  // @param entries to append (in order of 0 to append.length-1)
  // @param index of highest committed entry
  // @return 0, if server appended entries; otherwise, server's
  // current term
  public int appendEntries (int leaderTerm,
			    int leaderID,
			    int prevLogIndex,
			    int prevLogTerm,
			    Entry[] entries,
			    int leaderCommit) {
      synchronized (mLock) {
	  //cancel local timer
	  mTimer.cancel();
      
	  int term = mConfig.getCurrentTerm ();
	  int result = term;

      if (term>leaderTerm)  //request from stale leader
	  {
	      //tell him to quit
	      mTimer = this.scheduleTimer(ELECTION_TIMEOUT,mID); 
	      return term;
	  }
      mConfig.setCurrentTerm(Math.max(term, prevLogTerm), 0);
      if (entries == null)  //is heartbeat, no append, just update term and lastApplied
	  {
	      mLastApplied = Math.max(mLastApplied, mCommitIndex);
	      mTimer = this.scheduleTimer(ELECTION_TIMEOUT,mID);
	      return term;
	  }
      else
	  {
	      //client send request to me, should forward to leader
	      if (leaderID == mID)
		  {
		      //try each server
		      int num = mConfig.getNumServers();
		      for (int i = 1; i<=num;i++)
			  {
			      if (mID != i)
				  {
				      remoteAppendEntries(0,i,0, 0, 0, entries, 0);  //forward to leader
				  }
			  }
		  }
	      else  //true append
		  {
		      mConfig.setCurrentTerm(Math.max(term, prevLogTerm), 0);
		      if (prevLogIndex == -1)  //should append from start
			  {
			      mLog.insert(entries, -1, prevLogTerm);
			      result =0;
			      if (leaderCommit>mCommitIndex)  //only effecive when we append something
				  {
				      mCommitIndex = Math.min(leaderCommit, mLog.getLastIndex());
				      mLastApplied = Math.max(mLastApplied, mCommitIndex);
				  }
			  }
		      else
			  {
			      Entry testEntry = mLog.getEntry(prevLogIndex);
			      if (testEntry != null && testEntry.term == prevLogTerm) //same index, same term
				  {
				      //append, say yes and setCurrentTerm
				      mLog.insert(entries, prevLogIndex, prevLogTerm);
				      result = 0;
				      if (leaderCommit>mCommitIndex)
					  {
					      mCommitIndex = Math.min(leaderCommit, mLog.getLastIndex());
					      mLastApplied = Math.max(mLastApplied, mCommitIndex);
					  }
				  }  
			  }
		  }
	  }  
      mTimer = this.scheduleTimer(ELECTION_TIMEOUT,mID); 
      return result; 
      }
  }  

  // @param id of the timer that timed out
  public void handleTimeout (int timerID) {
    synchronized (mLock) {
	mTimer.cancel();
    	RaftMode mode = new CandidateMode();
    	RaftServerImpl.setMode (mode);
    }
  }
}
