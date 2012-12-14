package edu.cmu.scs.azurite.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.ListenerList;

import edu.cmu.scs.azurite.commands.runtime.RuntimeDC;
import edu.cmu.scs.azurite.commands.runtime.Segment;
import edu.cmu.scs.fluorite.commands.BaseDocumentChangeEvent;
import edu.cmu.scs.fluorite.commands.FileOpenCommand;
import edu.cmu.scs.fluorite.model.DocumentChangeListener;
import edu.cmu.scs.fluorite.model.EventRecorder;

public class RuntimeHistoryManager implements DocumentChangeListener {
	
	/**
	 * @author YoungSeok Yoon
	 * 
	 * A FileKey class is composed of a project name and a file name.
	 */
	private class FileKey {
		
		private final String mProjectName;
		private final String mFilePath;
		
		public FileKey(String projectName, String filePath) {
			mProjectName = projectName;
			mFilePath = filePath;
		}
		
		public String getProjectName() {
			return mProjectName;
		}
		
		public String getFilePath() {
			return mFilePath;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof FileKey)) {
				return false;
			}
			
			return	mProjectName.equals(((FileKey)obj).mProjectName) &&
					mFilePath.equals(((FileKey)obj).mFilePath);
		}
		
		@Override
		public int hashCode() {
			if (mProjectName == null && mFilePath == null)
				return 0;
			else if (mProjectName == null)
				return mFilePath.hashCode();
			else if (mFilePath == null)
				return mProjectName.hashCode();
			else
				return this.mProjectName.hashCode() ^ this.mFilePath.hashCode();
		}
		
	}
	
	private Map<FileKey, List<RuntimeDC>> mDocumentChanges;
	private FileKey mCurrentFileKey;
	
	private ListenerList mRuntimeDocumentChangeListeners;

	private List<Runnable> mScheduledTasks;
	
	private boolean mStarted;
	
	/**
	 * Basic constructor. Only use this public constructor for testing purposes!
	 * Otherwise, use <code>getInstance</code> static method instead.
	 */
	public RuntimeHistoryManager() {
		mDocumentChanges = new HashMap<FileKey, List<RuntimeDC>>();
		mCurrentFileKey = null;
		
		mRuntimeDocumentChangeListeners = new ListenerList();
		
		mScheduledTasks = new ArrayList<Runnable>();
		
		mStarted = false;
	}

	public void scheduleTask(Runnable runnable) {
		if (mStarted) {
			runnable.run();
		}
		else {
			mScheduledTasks.add(runnable);
		}
	}

	private static RuntimeHistoryManager _instance;
	/**
	 * Returns the singleton instance of this class.
	 * @return The singleton instance of this class.
	 */
	public static RuntimeHistoryManager getInstance() {
		if (_instance == null) {
			_instance = new RuntimeHistoryManager();
		}
		
		return _instance;
	}
	
	/**
	 * Start the Runtime History Manager, which is essentially the main model
	 * of Azurite 
	 */
	public void start() {
		EventRecorder.getInstance().addDocumentChangeListener(this);
	}
	
	/**
	 * Stop the Runtime History Manager.
	 */
	public void stop() {
		EventRecorder.getInstance().removeDocumentChangeListener(this);
	}

	/**
	 * Add runtime document change listener
	 * @param listener
	 */
	public void addRuntimeDocumentChangeListener(RuntimeDCListener listener) {
		mRuntimeDocumentChangeListeners.add(listener);
	}
	
	/**
	 * Remove runtime document change listener
	 * @param listener
	 */
	public void removeRuntimeDocumentChangeListener(RuntimeDCListener listener) {
		mRuntimeDocumentChangeListeners.remove(listener);
	}
	
	private void fireActiveFileChangedEvent(String projectName, String filePath) {
		for (Object listenerObj : mRuntimeDocumentChangeListeners.getListeners()) {
			((RuntimeDCListener)listenerObj).activeFileChanged(projectName, filePath);
		}
	}
	
	private void fireDocumentChangedEvent(RuntimeDC docChange) {
		for (Object listenerObj : mRuntimeDocumentChangeListeners.getListeners()) {
			((RuntimeDCListener)listenerObj).runtimeDCAdded(docChange);
		}
	}
	/**
	 * Returns all the runtime document changes associated with the current file.
	 * @return all the runtime document changes associated with the current file.
	 */
	public List<RuntimeDC> getRuntimeDocumentChanges() {
		return getRuntimeDocumentChanges(getCurrentFileKey());
	}
	
	/**
	 * Returns all the runtime document changes associated with the given file.
	 * @param filePath Fullpath to the source file
	 * @return all the runtime document changes associated with the given file.
	 */
	public List<RuntimeDC> getRuntimeDocumentChanges(FileKey key) {
		if (mDocumentChanges.containsKey(key)) {
			return mDocumentChanges.get(key);
		}
		
		return null;
	}
	
	/**
	 * Returns the current file path.
	 * @return the current file path.
	 */
	public String getCurrentFile() {
		if (mCurrentFileKey != null) {
			return mCurrentFileKey.getFilePath();
		}
		
		return null;
	}
	
	/**
	 * Returns the current project name.
	 * @return the current project name.
	 */
	public String getCurrentProject() {
		if (mCurrentFileKey != null) {
			return mCurrentFileKey.getProjectName();
		}
		
		return null;
	}
	
	private FileKey getCurrentFileKey() {
		return mCurrentFileKey;
	}
	
	private void setCurrentFileKey(FileKey newFileKey) {
		mCurrentFileKey = newFileKey;
	}
	
	public void activeFileChanged(FileOpenCommand foc) {
		activeFileChanged(foc.getProjectName(), foc.getFilePath(), foc.getSnapshot());
	}

	/**
	 * Simply updates the current file path.
	 */
	public void activeFileChanged(String projectName, String filePath, String snapshot) {
		FileKey key = new FileKey(projectName, filePath);
		setCurrentFileKey(key);
		
		if (!mDocumentChanges.containsKey(key)) {
			mDocumentChanges.put(key, new ArrayList<RuntimeDC>());
		}
		
		fireActiveFileChangedEvent(key.getProjectName(), key.getFilePath());
		
		// TODO extract diff.
/*		if (snapshot != null) {
			int maxLength = 0;
			for (RuntimeDC dc : getRuntimeDocumentChanges()) {
				for (Segment segment : dc.getAllSegments()) {
					int end = segment.getEffectiveEndOffset();
					if (end > maxLength) {
						maxLength = end;
					}
				}
			}
			
			String dummyDeletedText = new String(new char[maxLength]);
			
			boolean prevState = AbstractCommand.getIncrementCommandID();
			AbstractCommand.setIncrementCommandID(false);
			
			Replace replace = new Replace(0, maxLength, 0, 0, snapshot.length(), dummyDeletedText, snapshot, null);
			
			AbstractCommand.setIncrementCommandID(prevState);
			
			documentChangeFinalized(replace);
		}
*/	}

	public void documentChanged(BaseDocumentChangeEvent docChange) {
		// Do nothing here.
	}
	
	public void documentChangeFinalized(BaseDocumentChangeEvent docChange) {
		RuntimeDC runtimeDocChange = RuntimeDC.createRuntimeDocumentChange(docChange);
		
		List<RuntimeDC> list = mDocumentChanges.get(getCurrentFileKey());
		for (RuntimeDC existingDocChange : list) {
			runtimeDocChange.applyTo(existingDocChange);
		}
		
		list.add(runtimeDocChange);
		
		// Fire runtime document change event
		fireDocumentChangedEvent(runtimeDocChange);
	}
	
	public List<RuntimeDC> filterDocumentChangesByIds(final List<Integer> ids) {
		if (ids == null) {
			throw new IllegalArgumentException();
		}
		
		return filterDocumentChanges(new IRuntimeDCFilter() {
			@Override
			public boolean filter(RuntimeDC runtimeDC) {
				return ids.contains(runtimeDC.getOriginal().getCommandIndex());
			}
		});
	}
	
	public List<RuntimeDC> filterDocumentChangesByRegion(final int startOffset, final int endOffset) {
		return filterDocumentChanges(new IRuntimeDCFilter() {
			@Override
			public boolean filter(RuntimeDC runtimeDC) {
				List<Segment> segments = runtimeDC.getAllSegments();
				
				for (Segment segment : segments) {
					if (startOffset < segment.getEffectiveEndOffset() &&
						segment.getOffset() < endOffset) {
						return true;
					}
				}
				
				return false;
			}
		});
	}
	
	public List<RuntimeDC> filterDocumentChanges(IRuntimeDCFilter filter) {
		if (filter == null) {
			throw new IllegalArgumentException();
		}
		
		List<RuntimeDC> list = getRuntimeDocumentChanges();
		if (list == null) {
			throw new IllegalStateException();
		}
		
		List<RuntimeDC> result = new ArrayList<RuntimeDC>();
		for (RuntimeDC dc : list) {
			if (filter.filter(dc)) {
				result.add(dc);
			}
		}
		
		return result;
	}
}
