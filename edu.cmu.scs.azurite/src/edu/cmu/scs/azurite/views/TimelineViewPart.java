package edu.cmu.scs.azurite.views;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

import edu.cmu.scs.azurite.commands.runtime.RuntimeDC;
import edu.cmu.scs.azurite.jface.action.CommandAction;
import edu.cmu.scs.azurite.model.FileKey;
import edu.cmu.scs.azurite.model.OperationId;
import edu.cmu.scs.azurite.model.RuntimeDCListener;
import edu.cmu.scs.azurite.model.RuntimeHistoryManager;
import edu.cmu.scs.azurite.model.undo.SelectiveUndoEngine;
import edu.cmu.scs.azurite.plugin.Activator;
import edu.cmu.scs.fluorite.commands.AnnotateCommand;
import edu.cmu.scs.fluorite.commands.BaseDocumentChangeEvent;
import edu.cmu.scs.fluorite.commands.Delete;
import edu.cmu.scs.fluorite.commands.FileOpenCommand;
import edu.cmu.scs.fluorite.commands.ICommand;
import edu.cmu.scs.fluorite.commands.Insert;
import edu.cmu.scs.fluorite.commands.Replace;
import edu.cmu.scs.fluorite.model.CommandExecutionListener;
import edu.cmu.scs.fluorite.model.EventRecorder;
import edu.cmu.scs.fluorite.model.Events;
import edu.cmu.scs.fluorite.util.Utilities;

public class TimelineViewPart extends ViewPart implements RuntimeDCListener, CommandExecutionListener {
	
	private static TimelineViewPart me = null;
	private static String BROWSER_FUNC_PREFIX = "__AZURITE__";
	
	/**
	 * Not a singleton pattern per se.
	 * This object keeps the reference of itself upon GUI element creation.
	 * Provided just for convenience.
	 * @return The timelineviewpart's object. Could be null, if the view is not shown.
	 */
	public static TimelineViewPart getInstance() {
		return me;
	}

	private Browser browser;
	private ListenerList rectSelectionListenerList;
	
	public TimelineViewPart() {
		super();
		
		this.rectSelectionListenerList = new ListenerList();
	}
	
	public void addRectSelectionListener(RectSelectionListener listener) {
		this.rectSelectionListenerList.add(listener);
	}
	
	public void removeRectSelectionListener(RectSelectionListener listener) {
		this.rectSelectionListenerList.remove(listener);
	}
	
	public void fireRectSelectionChanged() {
		for (Object listenerObj : this.rectSelectionListenerList.getListeners()) {
			((RectSelectionListener)listenerObj).rectSelectionChanged();
		}
	}
	
	@Override
	public void createPartControl(Composite parent) {
		
		me = this;
		
		browser = new Browser(parent, SWT.NONE);
		addBrowserFunctions();
		moveToIndexPage();
		
		browser.addProgressListener(new ProgressListener() {
            
            public void completed(ProgressEvent event) {
            }
            
            public void changed(ProgressEvent event) {
            }
        });
		
		setupContextMenu();

		
		// Register to the EventRecorder.
		RuntimeHistoryManager.getInstance().addRuntimeDocumentChangeListener(this);
		EventRecorder.getInstance().addCommandExecutionListener(this);
	}

	private void setupContextMenu() {
		// Create the actions.
		final Map<String, String> paramMap = new HashMap<String, String>();
		
		final Action selectiveUndoAction = new CommandAction(
				"Selective Undo",
				"edu.cmu.scs.azurite.ui.commands.selectiveUndoCommand");
		
		ImageDescriptor isuIcon = Activator.getImageDescriptor("icons/undo_in_region.png");
		final Action interactiveSelectiveUndoAction = new CommandAction(
				"Interactive Selective Undo",
				"edu.cmu.scs.azurite.ui.commands.interactiveSelectiveUndoCommand");
		interactiveSelectiveUndoAction.setImageDescriptor(isuIcon);
		
		final Action undoEverythingAfterSelectionAction = new CommandAction(
				"Undo Everything After Selection",
				"edu.cmu.scs.azurite.ui.commands.undoEverythingAfterSelectionCommand");
		
		final Action jumpToTheAffectedCodeAction = new CommandAction(
				"Jump to the Affected Code in the Editor",
				"edu.cmu.scs.azurite.ui.commands.jumpToTheAffectedCodeCommand");

		paramMap.clear();
		paramMap.put("edu.cmu.scs.azurite.ui.commands.executeJSCode.codeToExecute", "showAllFilesEditedTogether();");
		final Action showAllFilesEditedTogetherAction = new CommandAction(
				"Show All Files Edited Together",
				"edu.cmu.scs.azurite.ui.commands.executeJSCode",
				paramMap);
		
		paramMap.clear();
		paramMap.put("edu.cmu.scs.azurite.ui.commands.executeJSCode.codeToExecute", "showSelectedFile();");
		final Action showThisFileOnlyAction = new CommandAction(
				"Show This File Only",
				"edu.cmu.scs.azurite.ui.commands.executeJSCode",
				paramMap);
		
		paramMap.clear();
		paramMap.put("edu.cmu.scs.azurite.ui.commands.executeJSCode.codeToExecute", "showAllFilesInProject();");
		final Action showAllFilesInTheSameProjectAction = new CommandAction(
				"Show All Files in the Same Project",
				"edu.cmu.scs.azurite.ui.commands.executeJSCode",
				paramMap);
		
		paramMap.clear();
		paramMap.put("edu.cmu.scs.azurite.ui.commands.executeJSCode.codeToExecute", "showAllFiles();");
		final Action showAllFilesAction = new CommandAction(
				"Show All Files",
				"edu.cmu.scs.azurite.ui.commands.executeJSCode",
				paramMap);
		
		// Setup the dynamic context menu
		MenuManager mgr = new MenuManager();
		mgr.setRemoveAllWhenShown(true);
		
		mgr.addMenuListener(new IMenuListener() {
			
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				try {
					String menuType = browser.evaluate("return cmenu.typeName;").toString();
					
					switch (menuType) {
						case "main_nothing": {
							manager.add(interactiveSelectiveUndoAction);
							break;
						}
						
						case "main_single": {
							manager.add(selectiveUndoAction);
							manager.add(interactiveSelectiveUndoAction);
							manager.add(undoEverythingAfterSelectionAction);
							manager.add(jumpToTheAffectedCodeAction);
							break;
						}
							
						case "main_multi": {
							manager.add(selectiveUndoAction);
							manager.add(interactiveSelectiveUndoAction);
							manager.add(undoEverythingAfterSelectionAction);
							manager.add(showAllFilesEditedTogetherAction);
							break;
						}
							
						case "file_in": {
							manager.add(showThisFileOnlyAction);
							manager.add(showAllFilesInTheSameProjectAction);
							manager.add(showAllFilesAction);
							break;
						}
							
						case "file_out": {
							manager.add(showAllFilesAction);
							break;
						}
						
						case "annotation": {
							long sid = ((Number) browser.evaluate("return global.lastAnnotation.sid;")).longValue();
							long id = ((Number) browser.evaluate("return global.lastAnnotation.id;")).longValue();
							String oidString = Long.toString(sid) + "_" + Long.toString(id);
							
							paramMap.clear();
							paramMap.put("edu.cmu.scs.azurite.ui.commands.undoAllFilesToThisPoint.annotationId", oidString);
							Action undoAllFilesToThisPointAction = new CommandAction(
									"Undo All Files to This Point",
									"edu.cmu.scs.azurite.ui.commands.undoAllFilesToThisPoint",
									paramMap);
							
							paramMap.clear();
							paramMap.put("edu.cmu.scs.azurite.ui.commands.undoCurrentFileToThisPoint.annotationId", oidString);
							Action undoCurrentFileToThisPointAction = new CommandAction(
									"Undo Current File to This Point",
									"edu.cmu.scs.azurite.ui.commands.undoCurrentFileToThisPoint",
									paramMap);
							
							manager.add(undoAllFilesToThisPointAction);
							manager.add(undoCurrentFileToThisPointAction);
							break;
						}
					}
					
					manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
				}
				catch (Exception e) {
					// Do nothing.
				}
			}
		});
		
		browser.setMenu(mgr.createContextMenu(browser));
	}

	private void moveToIndexPage() {
		// Retrieve the full URL of /html/index.html in our project.
		try {
			URL indexUrl = FileLocator.toFileURL(Platform.getBundle(
					"edu.cmu.scs.azurite").getEntry("/html/index.html"));
			browser.setUrl(indexUrl.toString());
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	private void addBrowserFunctions() {
		new UndoFunction(browser, BROWSER_FUNC_PREFIX + "selectiveUndo");
		new UndoEverythingAfterSelectionFunction(browser, BROWSER_FUNC_PREFIX + "undoEverythingAfterSelection");
		new InitializeFunction(browser, BROWSER_FUNC_PREFIX + "initialize");
		new JumpFunction(browser, BROWSER_FUNC_PREFIX + "jump");
		new LogFunction(browser, BROWSER_FUNC_PREFIX + "log");
		new GetInfoFunction(browser, BROWSER_FUNC_PREFIX + "getInfo");
		new MarkerMoveFunction(browser, BROWSER_FUNC_PREFIX + "markerMove");
		
		new EclipseCommandFunction(browser, BROWSER_FUNC_PREFIX + "eclipseCommand");
		
		new NotifySelectionChangedFunction(browser, BROWSER_FUNC_PREFIX + "notifySelectionChanged");
	}

	@Override
	public void dispose() {
		RuntimeHistoryManager.getInstance().removeRuntimeDocumentChangeListener(this);
		EventRecorder.getInstance().removeCommandExecutionListener(this);
		
		me = null;
		
		super.dispose();
	}

	@Override
	public void setFocus() {
		browser.setFocus();
	}


	class UndoFunction extends BrowserFunction {

		public UndoFunction(Browser browser, String name) {
			super(browser, name);
		}

		@Override
		public Object function(Object[] arguments) {
			if (arguments == null || arguments.length != 1 || arguments[0] == null) {
				return "fail";
			}
			
			try {
				// Convert everything into operation id.
				List<OperationId> ids = translateSelection(arguments[0]);
				
				Map<FileKey, List<RuntimeDC>> params = RuntimeHistoryManager
						.getInstance().extractFileDCMapFromOperationIds(ids);
				
				SelectiveUndoEngine.getInstance()
						.doSelectiveUndoOnMultipleFiles(params);
				
				return "ok";
			}
			catch (Exception e) {
				e.printStackTrace();
				return "fail";
			}
		}

	}
	
	class UndoEverythingAfterSelectionFunction extends BrowserFunction {

		public UndoEverythingAfterSelectionFunction(Browser browser, String name) {
			super(browser, name);
		}

		@Override
		public Object function(Object[] arguments) {
			if (arguments == null || arguments.length != 2
					|| !(arguments[0] instanceof Number)
					|| !(arguments[1] instanceof Number)) {
				return "fail";
			}
			
			try {
				long sid = ((Number)arguments[0]).longValue();
				long id = ((Number)arguments[1]).longValue();
				
				SelectiveUndoEngine.getInstance().doSelectiveUndo(
						RuntimeHistoryManager.getInstance()
								.filterDocumentChangesGreaterThanId(
										new OperationId(sid, id)));				
				
				return "ok";
			}
			catch (Exception e) {
				return "fail";
			}
		}
		
	}
	
	class InitializeFunction extends BrowserFunction {
		
		public InitializeFunction(Browser browser, String name) {
			super(browser, name);
		}
		
		@Override
		public Object function(Object[] arguments) {
            
			final long currentTimestamp = EventRecorder.getInstance().getStartTimestamp();
			
            // Read the existing runtime document changes.
            RuntimeHistoryManager.getInstance().scheduleTask(new Runnable() {
            	public void run() {
            		Display.getDefault().asyncExec(new Runnable() {
            			public void run() {
                    		RuntimeHistoryManager manager = RuntimeHistoryManager.getInstance(); 
                    		for (FileKey key : manager.getFileKeys()) {
                    			if (key.getProjectName() == null || key.getFilePath() == null) { continue; }
                    			
                    			addFile(key.getProjectName(), key.getFilePath());
                    			for (RuntimeDC dc : manager.getRuntimeDocumentChanges(key)) {
                    				addOperation(dc.getOriginal(), false, dc.getOriginal().getSessionId() == currentTimestamp);
                    			}
                    		}
                    		
                    		scrollToEnd();
            			}
            		});
            	}
            });
            
            browser.execute("layout();");
            
			return "ok";
		}
	}
	
	class LogFunction extends BrowserFunction {
		
		public LogFunction(Browser browser, String name) {
			super(browser, name);
		}
		
		@Override
		public Object function(Object[] arguments) {
			System.out.println( arguments[0] );
			
			return "ok";
		}
	}
	
	class JumpFunction extends BrowserFunction {

		public JumpFunction(Browser browser, String name) {
			super(browser, name);
		}
		
		@Override
		public Object function(Object[] arguments) {
			if (arguments == null || arguments.length != 4
					|| !(arguments[0] instanceof String)
					|| !(arguments[1] instanceof String)
					|| !(arguments[2] instanceof Number)
					|| !(arguments[3] instanceof Number)) {
				return "fail";
			}
			
			try {
				String projectName = (String)arguments[0];
				String filePath = (String)arguments[1];
				FileKey key = new FileKey(projectName, filePath);
				
				long sid = ((Number)arguments[2]).longValue();
				long id = ((Number)arguments[3]).longValue();
				
				File fileToOpen = new File(key.getFilePath());
				
				IEditorPart editor = null;
				if (fileToOpen.exists() && fileToOpen.isFile()) {
				    IFileStore fileStore = EFS.getLocalFileSystem().getStore(fileToOpen.toURI());
				    IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				 
				    try {
				        editor = IDE.openEditorOnFileStore( page, fileStore );
				    } catch ( PartInitException e ) {
				        //Put your exception handler here if you wish to
				    }
				} else {
				    //Do something if the file does not exist
				}

				// Move to the location.
				RuntimeDC runtimeDC = RuntimeHistoryManager.getInstance()
						.filterDocumentChangeById(key, new OperationId(sid, id));

				// Cannot retrieve the associated file.
				if (runtimeDC == null) {
					return "fail";
				}
				
				int offset = runtimeDC.getAllSegments().get(0).getOffset();
				if (editor != null) {
					ITextViewerExtension5 textViewerExt5 = Utilities
							.getTextViewerExtension5(editor);
					
					StyledText styledText = Utilities.getStyledText(editor);
					styledText.setSelection(textViewerExt5.modelOffset2WidgetOffset(offset));
					styledText.setFocus();
				}
				
				return "ok";
			}
			catch (Exception e) {
				return "fail";
			}
		}
		
	}
	
	class GetInfoFunction extends BrowserFunction {

		public GetInfoFunction(Browser browser, String name) {
			super(browser, name);
		}

		@Override
		public Object function(Object[] arguments) {
			if (arguments == null || arguments.length != 4
					|| !(arguments[0] instanceof String)
					|| !(arguments[1] instanceof String)
					|| !(arguments[2] instanceof Number)
					|| !(arguments[3] instanceof Number)) {
				return "fail";
			}
			
			try {
				String projectName = (String)arguments[0];
				String filePath = (String)arguments[1];
				FileKey key = new FileKey(projectName, filePath);
				
				long sid = ((Number)arguments[2]).longValue();
				long id = ((Number)arguments[3]).longValue();
				OperationId oid = new OperationId(sid, id);
				
				RuntimeDC runtimeDC = RuntimeHistoryManager.getInstance()
						.filterDocumentChangeByIdWithoutCalculating(key, oid);
				
				if (runtimeDC != null) {
					String result = runtimeDC.getHtmlInfo();
					if (result != null) {
						return result;
					}
				}
				
				return "unknown";
			}
			catch (Exception e) {
				return "unknown";
			}
		}
		
	}
	
	class MarkerMoveFunction extends BrowserFunction {

		public MarkerMoveFunction(Browser browser, String name) {
			super(browser, name);
		}

		@Override
		public Object function(Object[] arguments) {
			if (arguments.length != 1 || !(arguments[0] instanceof Number)) {
				return "fail";
			}
			
			long absTimestamp = ((Number)arguments[0]).longValue();
			
			for (CodeHistoryDiffViewPart view : 
					CodeHistoryDiffViewPart.getInstances()) {
				view.selectVersionWithAbsTimestamp(absTimestamp);
			}
			
			return "ok";
		}
		
	}
	
	class EclipseCommandFunction extends BrowserFunction {

		public EclipseCommandFunction(Browser browser, String name) {
			super(browser, name);
		}
		
		@Override
		public Object function(Object[] arguments) {
			if (arguments.length != 1 || !(arguments[0] instanceof String)) {
				return "fail";
			}
			
			String eclipseCmdId = (String)arguments[0];
			
			IHandlerService handlerService = (IHandlerService) getSite().getService(IHandlerService.class);
			try {
				handlerService.executeCommand(eclipseCmdId, null);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			
			return "ok";
		}
	}
	
	class NotifySelectionChangedFunction extends BrowserFunction {

		public NotifySelectionChangedFunction(Browser browser, String name) {
			super(browser, name);
		}

		@Override
		public Object function(Object[] arguments) {
			try {
				fireRectSelectionChanged();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			
			return "ok";
		}
		
	}
	
	@Override
	public void activeFileChanged(String projectName, String filePath) {
		if (projectName == null || filePath == null) {
			// Some non-text file is opened maybe?
			return;
		}
		
		addFile(projectName, filePath);
	}

	@Override
	public void runtimeDCAdded(RuntimeDC docChange) {
		// Do nothing here. Now the blocks are added at real time.
	}
	
	@Override
	public void documentChangeAdded(BaseDocumentChangeEvent docChange) {
		addOperation(docChange, true, true);
	}

	private void addFile(String projectName, String filePath) {
		String executeStr = getAddFileString(projectName, filePath);
		browser.execute(executeStr);
	}

	private String getAddFileString(String projectName, String filePath) {
		String executeStr = String.format("addFile('%1$s', '%2$s');",
				projectName,
				filePath == null ? "null" : filePath.replace('\\', '/'));	// avoid escaping..
		return executeStr;
	}
	
	private void addAnnotation(AnnotateCommand annotate) {
		String executeStr = getAddAnnotationString(annotate);
		browser.execute(executeStr);
	}
	
	private String getAddAnnotationString(AnnotateCommand annotate) {
		String comment = annotate.getComment();
		if (comment == null) {
			comment = "unnamed";
		}
		
		String executeStr = String.format("addAnnotation(%1$d, %2$d, %3$d, \'%4$s\');",
				annotate.getSessionId(),
				annotate.getCommandIndex(),
				annotate.getTimestamp(),
				comment);
		
		return executeStr;
	}

	private void addOperation(BaseDocumentChangeEvent docChange, boolean scroll, boolean current) {
		String executeStr = getAddOperationString(docChange, scroll, true, current);
		browser.execute(executeStr);
	}

	private void addOperations(Events events) {
		// Store the current file.
		browser.execute("pushCurrentFile();");
		
		// Add the operations
		StringBuilder builder = new StringBuilder();
		
		long start = System.currentTimeMillis();
		
		for (ICommand command : events.getCommands()) {
			if (command instanceof AnnotateCommand) {
				addAnnotation((AnnotateCommand)command);
				continue;
			}
			
			if (!(command instanceof BaseDocumentChangeEvent)) {
				continue;
			}
			
			BaseDocumentChangeEvent docChange = (BaseDocumentChangeEvent)command;
			if (docChange instanceof FileOpenCommand) {
				FileOpenCommand foc = (FileOpenCommand)docChange;
				if (foc.getFilePath() != null) {
					builder.append(getAddFileString(foc.getProjectName(), foc.getFilePath()));
				}
//				activeFileChanged(foc.getProjectName(), foc.getFilePath());
			} else {
				builder.append(getAddOperationString(docChange, false, false, false));
//				addOperation(docChange, false);
			}
		}
		
		long end = System.currentTimeMillis();
		System.out.println("Building String: " + (end - start) + "ms");
		
		start = System.currentTimeMillis();
		browser.execute(builder.toString());
		end = System.currentTimeMillis();
		System.out.println("Executing String: " + (end - start) + "ms");
		
		// Restore the last file
		browser.execute("popCurrentFile();");
	}

	private String getAddOperationString(BaseDocumentChangeEvent docChange,
			boolean scroll, boolean layout, boolean current) {
		String executeStr = String.format("addOperation(%1$d, %2$d, %3$d, %4$d, %5$f, %6$f, %7$d, %8$s, %9$s, %10$s);",
				docChange.getSessionId(),
				docChange.getCommandIndex(),
				docChange.getTimestamp(),
				docChange.getTimestamp2(),
				docChange.getY1(),
				docChange.getY2(),
				getTypeIndex(docChange),
				Boolean.toString(scroll),
				Boolean.toString(layout),
				Boolean.toString(current));
		return executeStr;
	}
	
	private int getTypeIndex(BaseDocumentChangeEvent docChange) {
		if (docChange instanceof Insert) {
			return 0;
		}
		else if (docChange instanceof Delete) {
			return 1;
		}
		else if (docChange instanceof Replace) {
			return 2;
		}
		else {
			return -1;
		}
	}

	@Override
	public void documentChangeUpdated(BaseDocumentChangeEvent docChange) {
		String executeStr = String.format(
				"updateOperation(%1$d, %2$d, %3$d, %4$f, %5$f, true);",
				docChange.getSessionId(),
				docChange.getCommandIndex(),
				docChange.getTimestamp2(),
				docChange.getY1(),
				docChange.getY2());
		browser.execute(executeStr);
	}

	/**
	 * Add selections to the timeline view. Must be called from the SWT EDT.
	 * @param ids list of ids to be selected
	 * @param clearSelection indicates whether the existing selections should be discarded before adding new selections.
	 */
	public void addSelection(List<OperationId> ids, boolean clearSelection) {
		StringBuffer buffer = new StringBuffer();
		buffer.append("addSelectionsByIds(");
		
		getJavaScriptListFromOperationIds(buffer, ids);
		
		buffer.append(", " + Boolean.toString(clearSelection) + ");");
		
		browser.execute(buffer.toString());
	}
	
	public void removeSelection(List<OperationId> ids) {
		StringBuffer buffer = new StringBuffer();
		buffer.append("removeSelectionsByIds(");
		
		getJavaScriptListFromOperationIds(buffer, ids);
		
		buffer.append(");");
		
		browser.execute(buffer.toString());
	}

	private void getJavaScriptListFromOperationIds(StringBuffer buffer,
			List<OperationId> ids) {
		buffer.append("[");
		Iterator<OperationId> it;
		
		it = ids.iterator();
		if (it.hasNext()) {
			buffer.append(Long.toString(it.next().sid));
			while (it.hasNext()) {
				buffer.append(", " + it.next().sid);
			}
		}
		
		buffer.append("], [");
		
		it = ids.iterator();
		if (it.hasNext()) {
			buffer.append(Long.toString(it.next().id));
			while (it.hasNext()) {
				buffer.append(", " + it.next().id);
			}
		}
		
		buffer.append("]");
	}
	
	public void activateFirebugLite() {
        browser.setUrl("javascript:(function(F,i,r,e,b,u,g,L,I,T,E){if(F.getElementById(b))return;E=F[i+'NS']&&F.documentElement.namespaceURI;E=E?F[i+'NS'](E,'script'):F[i]('script');E[r]('id',b);E[r]('src',I+g+T);E[r](b,u);(F[e]('head')[0]||F[e]('body')[0]).appendChild(E);E=new%20Image;E[r]('src',I+L);})(document,'createElement','setAttribute','getElementsByTagName','FirebugLite','3','releases/lite/1.3/firebug-lite.js','releases/lite/latest/skin/xp/sprite.png','https://getfirebug.com/','#startOpened');");
	}

	@Override
	public void pastLogsRead(List<Events> listEvents) {
		if (listEvents == null) {
			throw new IllegalArgumentException();
		}
		
		if (listEvents.isEmpty()) { 
			return;
		}

		// Add all the things.
		for (Events events : listEvents) {
			addOperations(events);
		}
		
		// Update the data.
		browser.execute("layout();");
	}
	
	private void scrollToEnd() {
		browser.execute("scrollToEnd()");
	}
	
	public void executeJSCode(String codeToExecute) {
		browser.execute(codeToExecute);
	}
	
	public Object evaluateJSCode(String codeToExecute) {
		return browser.evaluate(codeToExecute);
	}
	
	public void refresh() {
		moveToIndexPage();
	}
	
	public void showMarker(long absTimestamp) {
		browser.execute("showMarker(" + absTimestamp + ");");
	}
	
	public void hideMarker() {
		browser.execute("hideMarker();");
	}
	
	public int getSelectedRectsCount() {
		try {
			Object result = evaluateJSCode("return global.selected.length;");
			if (result instanceof Number) {
				return ((Number) result).intValue();
			}
			else {
				return 0;
			}
		} catch (SWTException e) {
			return 0;
		}
	}
	
	public List<OperationId> getRectSelection() {
		Object selected = evaluateJSCode("return getStandardRectSelection();");
		return translateSelection(selected);
	}

	public static List<OperationId> translateSelection(Object selected) {
		Object[] selectedArray = (Object[]) selected;
		
		List<OperationId> ids = new ArrayList<OperationId>();
		for (Object element : selectedArray) {
			if (!(element instanceof Object[])) { continue; }
			
			Object[] idComponents = (Object[])element;
			if (idComponents.length == 2 &&
					idComponents[0] instanceof Number &&
					idComponents[1] instanceof Number) {
				Number sid = (Number)idComponents[0];
				Number id = (Number)idComponents[1];
				
				ids.add(new OperationId(sid.longValue(), id.longValue()));
			}
		}
		return ids;
	}

	@Override
	public void commandExecuted(ICommand command) {
		// Somehow add the annotation.
		if (command instanceof AnnotateCommand) {
			addAnnotation((AnnotateCommand)command);
		}
	}
	
}
