package org.gampiot.robok.ui.fragments.editor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.annotation.IdRes 

import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion

import org.gampiot.robok.R
import org.gampiot.robok.databinding.FragmentEditorBinding
import org.gampiot.robok.feature.editor.EditorListener
import org.gampiot.robok.feature.component.terminal.RobokTerminal
import org.gampiot.robok.feature.res.Strings
import org.gampiot.robok.feature.util.base.RobokFragment
import org.gampiot.robok.ui.fragments.build.output.OutputFragment
import org.gampiot.robok.ui.fragments.editor.logs.LogsFragment
import org.gampiot.robok.ui.fragments.editor.diagnostic.DiagnosticFragment

import robok.compiler.logic.LogicCompiler
import robok.compiler.logic.LogicCompilerListener
import robok.diagnostic.logic.DiagnosticListener

class EditorFragment() : RobokFragment() {

    var _binding: FragmentEditorBinding? = null
    val binding get() = _binding!!
    val handler = Handler(Looper.getMainLooper())
    val diagnosticTimeoutRunnable = object : Runnable {
        override fun run() {
            binding.diagnosticStatusImage.setBackgroundResource(R.drawable.ic_success_24)
            binding.diagnosticStatusDotProgress.visibility = View.INVISIBLE
            binding.diagnosticStatusImage.visibility = View.VISIBLE
        }
    }

    val diagnosticStandTime : Long = 800

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val  paths = /sdcard/Robok/
        loadFilesTotree(paths, recyclerview, "Robok");
        val path = arguments?.getString(PROJECT_PATH) ?: "/sdcard/Robok/Projects/Default/"
        val terminal = RobokTerminal(requireContext())
        val compilerListener = object : LogicCompilerListener {
            override fun onCompiling(log: String) {
                terminal.addLog(log)
            }

            override fun onCompiled(output: String) {
                val outputFragment = OutputFragment()
                outputFragment.addOutput(requireContext(), layoutInflater, view as ViewGroup, output)

                Snackbar.make(binding.root, Strings.message_compiled, Snackbar.LENGTH_LONG)
                    .setAction(Strings.go_to_outputs) {
                        openFragment(outputFragment)
                        terminal.dismiss()
                    }
                    .show()
            }
        }
        val compiler = LogicCompiler(requireContext(), compilerListener)

        binding.runButton.setOnClickListener {
            val code = binding.codeEditor.text.toString()
            terminal.show()
            compiler.compile(code)
        }

        binding.seeLogs.setOnClickListener {
            terminal.show()
        }

        configureTabLayout()
        configureToolbar()
        configureDrawer()
        configureEditor()
    }

    fun configureTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    when (it.text) {
                        getString(Strings.text_logs) -> {
                            openFragment(R.id.drawer_editor_right_fragment_container, LogsFragment())
                        }
                        getString(Strings.text_diagnostic) -> {
                            openFragment(R.id.drawer_editor_right_fragment_container, DiagnosticFragment())
                        }
                    }
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
        })
    }

    fun configureToolbar() {
        binding.diagnosticStatusDotProgress.startAnimation()
        binding.toolbar.setNavigationOnClickListener {
              if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
              } else {
                    binding.drawerLayout.openDrawer(GravityCompat.START)
              }
        }
        binding.diagnosticStatusImage.setOnClickListener {
              if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
              } else {
                    binding.drawerLayout.openDrawer(GravityCompat.END)
              }
              binding.tabLayout.getTabAt(1)?.select()
        }
    }

    fun configureDrawer() {
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            var leftDrawerOffset = 0f
            var rightDrawerOffset = 0f

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                val drawerWidth = drawerView.width
                when (drawerView.id) {
                    R.id.navigation_view_left -> {
                        leftDrawerOffset = drawerWidth * slideOffset
                        binding.content.translationX = leftDrawerOffset
                    }
                    R.id.navigation_view_right -> {
                        rightDrawerOffset = drawerWidth * slideOffset
                        binding.content.translationX = -rightDrawerOffset
                    }
                }
            }

            override fun onDrawerOpened(drawerView: View) {}
            override fun onDrawerClosed(drawerView: View) {
                binding.content.translationX = 0f
                leftDrawerOffset = 0f
                rightDrawerOffset = 0f
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })
    }

    fun configureEditor() {
        val diagnosticListener = object : DiagnosticListener {
            override fun onDiagnosticStatusReceive(isError: Boolean) {
                handler.removeCallbacks(diagnosticTimeoutRunnable)
                
                if (isError) {
                    binding.diagnosticStatusImage.setBackgroundResource(R.drawable.ic_error_24)
                } else {
                    binding.diagnosticStatusImage.setBackgroundResource(R.drawable.ic_success_24)
                }
                binding.diagnosticStatusDotProgress.visibility = View.INVISIBLE
                binding.diagnosticStatusImage.visibility = View.VISIBLE
            }

            override fun onDiagnosticReceive(line: Int, positionStart: Int, positionEnd: Int, msg: String) {
                binding.codeEditor.addDiagnosticInEditor(positionStart, positionEnd, DiagnosticRegion.SEVERITY_ERROR, msg)
                onDiagnosticStatusReceive(true)
            }
        }

        val editorListener = object : EditorListener {
            override fun onEditorTextChange() {
                updateUndoRedo()
                binding.diagnosticStatusDotProgress.visibility = View.VISIBLE
                binding.diagnosticStatusImage.visibility = View.INVISIBLE
                
                handler.removeCallbacks(diagnosticTimeoutRunnable)
                handler.postDelayed(diagnosticTimeoutRunnable, diagnosticStandTime)
            }
        }

        binding.codeEditor.setDiagnosticListener(diagnosticListener)
        binding.codeEditor.setEditorListener(editorListener)

        binding.undo.setOnClickListener {
            binding.codeEditor.undo()
            updateUndoRedo()
        }
        binding.redo.setOnClickListener {
            binding.codeEditor.redo()
            updateUndoRedo()
        }
        handler.postDelayed(diagnosticTimeoutRunnable, diagnosticStandTime)
    }
    
    fun updateUndoRedo() {
        binding.redo?.let {
            it.isEnabled = binding.codeEditor.isCanRedo()
        }
        binding.undo?.let {
            it.isEnabled = binding.codeEditor.isCanUndo()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val PROJECT_PATH = "arg_path"

        @JvmStatic
        fun newInstance(path: String): EditorFragment {
            return EditorFragment().apply {
                arguments = Bundle().apply {
                    putString(PROJECT_PATH, path)
                }
            }
        }
    }
    	private TreeViewList.TreeViewAdapter adapter;
		private List<TreeViewList.TreeNode> nodes2;
		private TreeViewList.TreeNode<TreeViewList.Dir> node;
	
		public void loadFilesTotree(String path, final RecyclerView recycler, String rootFolderName){
		
				TreeViewList.isPath = true;
		TreeViewList.backgroundColor = Color.TRANSPARENT;
		TreeViewList.textColor = R.attr.colorOnSurface;
				recycler.setBackgroundColor(TreeViewList.backgroundColor);
		
		
				nodes2 = new ArrayList<>();
				node = new TreeViewList.TreeNode<>(new TreeViewList.Dir(rootFolderName));
				nodes2.add(node);
					
				 
				initData2(path, node);
					
						
						recycler.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
		
				adapter = new TreeViewList.TreeViewAdapter(nodes2, Arrays.asList(new TreeViewList.FileNodeBinder(), new TreeViewList.DirectoryNodeBinder()));
				// whether collapse child nodes when their parent node was close.
		//        adapter.ifCollapseChildWhileCollapseParent(true);
				adapter.setOnTreeNodeListener(new TreeViewList.TreeViewAdapter.OnTreeNodeListener() {
						@Override
						public boolean onClick(String clickedPath, TreeViewList.TreeNode node, RecyclerView.ViewHolder holder) {
								if (!node.isLeaf()) {
										//Update and toggle the node.
										onToggle(!node.isExpand(), holder);
					//                    if (!node.isExpand())
					//                        adapter.collapseBrotherNode(node);
								}
								
				// simple click
				if (FileUtil.isDirectory(clickedPath)) {
					
				}
				else {
					if (FileUtil.isFile(clickedPath)) {
						SketchwareUtil.showMessage(getApplicationContext(), clickedPath);
					}
				}
				
				
								return false;
						}
			
						@Override
						public void onToggle(boolean isExpand, RecyclerView.ViewHolder holder) {
								TreeViewList.DirectoryNodeBinder.ViewHolder dirViewHolder = (TreeViewList.DirectoryNodeBinder.ViewHolder) holder;
								final ImageView ivArrow = dirViewHolder.getIvArrow();
								int rotateDegree = isExpand ? 90 : -90;
								ivArrow.animate().rotationBy(rotateDegree)
										.start();
						}
						
						
						@Override
						public void onLongClick(String clickedPath){
				
							//	Toast.makeText(getApplicationContext(), "long clicked : "+ clickedPath, Toast.LENGTH_SHORT).show();
				 
				
						}
						
				});
				recycler.setAdapter(adapter);
						
						
		
		
				
		
				
		}
		
		public void initData2(String path, final TreeViewList.TreeNode<TreeViewList.Dir> dir){
		
		final String[] pathStr = {path};
		
		new Thread(new Runnable() {
				@Override
				public void run() {
						Looper.prepare();
								
				
						ArrayList<String> rootDir = new ArrayList<>();
				
						FileUtil.listDir(pathStr[0], rootDir);
				
						for (String one : rootDir){
								if (FileUtil.isFile(one)){
										dir.addChild(new TreeViewList.TreeNode<>(new TreeViewList.File(one)));
								} else if (FileUtil.isDirectory(one)) {
										TreeViewList.TreeNode<TreeViewList.Dir> dirTree = new TreeViewList.TreeNode<>(new TreeViewList.Dir(one));
										dir.addChild(dirTree);
										initData2(one, dirTree);
								}
						}
						
						
					}
		}).start();
		
				
		}
		
	
	
	
	public static class TreeViewList {
		
		    public static boolean isPath = false;
		    public static int textColor = 0xFF000000;
		    public static int backgroundColor = 0xFFFFFFFF;
		
		
		    public static class TreeNode<T extends LayoutItemType> implements Cloneable {
			        private T content;
			        private TreeNode parent;
			        private List<TreeNode> childList;
			        private boolean isExpand;
			        private boolean isLocked;
			        //the tree high
			        private int height = UNDEFINE;
			
			        private static final int UNDEFINE = -1;
			
			        public TreeNode(@NonNull T content) {
				            this.content = content;
				            this.childList = new ArrayList<>();
				        }
			
			        public int getHeight() {
				            if (isRoot())
				                height = 0;
				            else if (height == UNDEFINE)
				                height = parent.getHeight() + 1;
				            return height;
				        }
			
			        public boolean isRoot() {
				            return parent == null;
				        }
			
			        public boolean isLeaf() {
				            return childList == null || childList.isEmpty();
				        }
			
			        public void setContent(T content) {
				            this.content = content;
				        }
			
			        public T getContent() {
				            return content;
				        }
			
			        public List<TreeNode> getChildList() {
				            return childList;
				        }
			
			        public void setChildList(List<TreeNode> childList) {
				            this.childList.clear();
				            for (TreeNode treeNode : childList) {
					                addChild(treeNode);
					            }
				        }
			
			        public TreeNode addChild(TreeNode node) {
				            if (childList == null)
				                childList = new ArrayList<>();
				            childList.add(node);
				            node.parent = this;
				            return this;
				        }
			
			        public boolean toggle() {
				            isExpand = !isExpand;
				            return isExpand;
				        }
			
			        public void collapse() {
				            if (isExpand) {
					                isExpand = false;
					            }
				        }
			
			        public void collapseAll() {
				            if (childList == null || childList.isEmpty()) {
					                return;
					            }
				            for (TreeNode child : this.childList) {
					                child.collapseAll();
					            }
				        }
			
			        public void expand() {
				            if (!isExpand) {
					                isExpand = true;
					            }
				        }
			
			        public void expandAll() {
				            expand();
				            if (childList == null || childList.isEmpty()) {
					                return;
					            }
				            for (TreeNode child : this.childList) {
					                child.expandAll();
					            }
				        }
			
			        public boolean isExpand() {
				            return isExpand;
				        }
			
			        public void setParent(TreeNode parent) {
				            this.parent = parent;
				        }
			
			        public TreeNode getParent() {
				            return parent;
				        }
			
			        public TreeNode<T> lock() {
				            isLocked = true;
				            return this;
				        }
			
			        public TreeNode<T> unlock() {
				            isLocked = false;
				            return this;
				        }
			
			        public boolean isLocked() {
				            return isLocked;
				        }
			
			        @Override
			        public String toString() {
				            return "TreeNode{" +
				                    "content=" + this.content +
				                    ", parent=" + (parent == null ? "null" : parent.getContent().toString()) +
				                    ", childList=" + (childList == null ? "null" : childList.toString()) +
				                    ", isExpand=" + isExpand +
				                    '}';
				        }
			
			        @Override
			        protected TreeNode<T> clone() throws CloneNotSupportedException {
				            TreeNode<T> clone = new TreeNode<>(this.content);
				            clone.isExpand = this.isExpand;
				            return clone;
				        }
			    }
		
		
		    //interface
		    public interface LayoutItemType {
			        int getLayoutId();
			    }
		
		
		    // Tree View Adapter
		
		
		    public static class TreeViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
			        private static final String KEY_IS_EXPAND = "IS_EXPAND";
			        private final List<? extends TreeViewBinder> viewBinders;
			        private List<TreeNode> displayNodes;
			        private int padding = 30;
			        private OnTreeNodeListener onTreeNodeListener;
			        private boolean toCollapseChild;
			
			        public TreeViewAdapter(List<? extends TreeViewBinder> viewBinders) {
				            this(null, viewBinders);
				        }
			
			        public TreeViewAdapter(List<TreeNode> nodes, List<? extends TreeViewBinder> viewBinders) {
				            displayNodes = new ArrayList<>();
				            if (nodes != null)
				                findDisplayNodes(nodes);
				            this.viewBinders = viewBinders;
				        }
			
			        private void findDisplayNodes(List<TreeNode> nodes) {
				            for (TreeNode node : nodes) {
					                displayNodes.add(node);
					                if (!node.isLeaf() && node.isExpand())
					                    findDisplayNodes(node.getChildList());
					            }
				        }
			
			        @Override
			        public int getItemViewType(int position) {
				            return displayNodes.get(position).getContent().getLayoutId();
				        }
			
			        @Override
			        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
				            View v = LayoutInflater.from(parent.getContext())
				                    .inflate(viewType, parent, false);
				            if (viewBinders.size() == 1)
				                return viewBinders.get(0).provideViewHolder(v);
				            for (TreeViewBinder viewBinder : viewBinders) {
					                if (viewBinder.getLayoutId() == viewType)
					                    return viewBinder.provideViewHolder(v);
					            }
				            return viewBinders.get(0).provideViewHolder(v);
				        }
			
			        @Override
			        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, List<Object> payloads) {
				            if (payloads != null && !payloads.isEmpty()) {
					                Bundle b = (Bundle) payloads.get(0);
					                for (String key : b.keySet()) {
						                    switch (key) {
							                        case KEY_IS_EXPAND:
							                            if (onTreeNodeListener != null)
							                                onTreeNodeListener.onToggle(b.getBoolean(key), holder);
							                            break;
							                    }
						                }
					            }
				            super.onBindViewHolder(holder, position, payloads);
				        }
			
			        @Override
			        public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
				            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
					                holder.itemView.setPaddingRelative(displayNodes.get(position).getHeight() * padding, 3, 3, 3);
					            }else {
					                holder.itemView.setPadding(displayNodes.get(position).getHeight() * padding, 3, 3, 3);
					            }
				
				            final TextView txt = holder.itemView.findViewById(R.id.tv_name);
				
				            txt.setTextColor(textColor);
				            holder.itemView.setBackgroundColor(backgroundColor);
				
				            final String clickedPath[] = {""};
				
				            holder.itemView.setOnClickListener(new View.OnClickListener() {
					                @Override
					                public void onClick(View v) {
						                    TreeNode selectedNode = displayNodes.get(holder.getLayoutPosition());
						                    // Prevent multi-click during the short interval.
						                    try {
							                        long lastClickTime = (long) holder.itemView.getTag();
							                       if (System.currentTimeMillis() - lastClickTime < 500)
							                            return;
							                    } catch (Exception e) {
							                        holder.itemView.setTag(System.currentTimeMillis());
							                    }
						                    holder.itemView.setTag(System.currentTimeMillis());
						
						                    
						
						
						
						                    try {
							                        Dir dirNode = (Dir) selectedNode.getContent();
							                        clickedPath[0] = dirNode.dirName;
							                    } catch (Exception e){
							                        File fileNode = (File) selectedNode.getContent();
							                        clickedPath[0] = fileNode.fileName;
							                    }
						
						                    if (onTreeNodeListener != null && onTreeNodeListener.onClick(clickedPath[0],
						                            selectedNode, holder))
						                        return;
						                    if (selectedNode.isLeaf())
						                        return;
						                    // This TreeNode was locked to click.
						                    if (selectedNode.isLocked()) return;
						                    boolean isExpand = selectedNode.isExpand();
						                    int positionStart = displayNodes.indexOf(selectedNode) + 1;
						                    if (!isExpand) {
							                        notifyItemRangeInserted(positionStart, addChildNodes(selectedNode, positionStart));
							                    } else {
							                        notifyItemRangeRemoved(positionStart, removeChildNodes(selectedNode, true));
							                    }
						                }
					            });
							
							
							holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
					                @Override
					                public boolean onLongClick(View v) {
						                    TreeNode selectedNode = displayNodes.get(holder.getLayoutPosition());
						
						                    try {
							                        Dir dirNode = (Dir) selectedNode.getContent();
							                        clickedPath[0] = dirNode.dirName;
							                    } catch (Exception e){
							                        File fileNode = (File) selectedNode.getContent();
							                        clickedPath[0] = fileNode.fileName;
							                    }
						
						                    onTreeNodeListener.onLongClick(clickedPath[0]);
						
						
						                    return true;
						                }
					            });
							
							
				            for (TreeViewBinder viewBinder : viewBinders) {
					                if (viewBinder.getLayoutId() == displayNodes.get(position).getContent().getLayoutId())
					                    viewBinder.bindView(holder, position, displayNodes.get(position));
					            }
				        }
			
			        private int addChildNodes(TreeNode pNode, int startIndex) {
				            List<TreeNode> childList = pNode.getChildList();
				            int addChildCount = 0;
				            for (TreeNode treeNode : childList) {
					                displayNodes.add(startIndex + addChildCount++, treeNode);
					                if (treeNode.isExpand()) {
						                    addChildCount += addChildNodes(treeNode, startIndex + addChildCount);
						                }
					            }
				            if (!pNode.isExpand())
				                pNode.toggle();
				            return addChildCount;
				        }
			
			        private int removeChildNodes(TreeNode pNode) {
				            return removeChildNodes(pNode, true);
				        }
			
			        private int removeChildNodes(TreeNode pNode, boolean shouldToggle) {
				            if (pNode.isLeaf())
				                return 0;
				            List<TreeNode> childList = pNode.getChildList();
				            int removeChildCount = childList.size();
				            displayNodes.removeAll(childList);
				            for (TreeNode child : childList) {
					                if (child.isExpand()) {
						                    if (toCollapseChild)
						                        child.toggle();
						                    removeChildCount += removeChildNodes(child, false);
						                }
					            }
				            if (shouldToggle)
				                pNode.toggle();
				            return removeChildCount;
				        }
			
			        @Override
			        public int getItemCount() {
				            return displayNodes == null ? 0 : displayNodes.size();
				        }
			
			        public void setPadding(int padding) {
				            this.padding = padding;
				        }
			
			        public void ifCollapseChildWhileCollapseParent(boolean toCollapseChild) {
				            this.toCollapseChild = toCollapseChild;
				        }
			
			        public void setOnTreeNodeListener(OnTreeNodeListener onTreeNodeListener) {
				            this.onTreeNodeListener = onTreeNodeListener;
				        }
			
			        public interface OnTreeNodeListener {
				            /**
             * called when TreeNodes were clicked.
             * @return weather consume the click event.
             */
				            boolean onClick(String clickedPath, TreeNode node, RecyclerView.ViewHolder holder);
				
				            /**
             * called when TreeNodes were toggle.
             * @param isExpand the status of TreeNodes after being toggled.
             */
				            void onToggle(boolean isExpand, RecyclerView.ViewHolder holder);
							
							
							//long clickedPath
							void onLongClick(String clickedPath);
				        }
			
			        public void refresh(List<TreeNode> treeNodes) {
				            displayNodes.clear();
				            findDisplayNodes(treeNodes);
				            notifyDataSetChanged();
				        }
			
			        public Iterator<TreeNode> getDisplayNodesIterator() {
				            return displayNodes.iterator();
				        }
			
			        private void notifyDiff(final List<TreeNode> temp) {
				            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
					                @Override
					                public int getOldListSize() {
						                    return temp.size();
						                }
					
					                @Override
					                public int getNewListSize() {
						                    return displayNodes.size();
						                }
					
					                // judge if the same items
					                @Override
					                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
						                    return TreeViewAdapter.this.areItemsTheSame(temp.get(oldItemPosition), displayNodes.get(newItemPosition));
						                }
					
					                // if they are the same items, whether the contents has bean changed.
					                @Override
					                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
						                    return TreeViewAdapter.this.areContentsTheSame(temp.get(oldItemPosition), displayNodes.get(newItemPosition));
						                }
					
					                @Nullable
					                @Override
					                public Object getChangePayload(int oldItemPosition, int newItemPosition) {
						                    return TreeViewAdapter.this.getChangePayload(temp.get(oldItemPosition), displayNodes.get(newItemPosition));
						                }
					            });
				            diffResult.dispatchUpdatesTo(this);
				        }
			
			        private Object getChangePayload(TreeNode oldNode, TreeNode newNode) {
				            Bundle diffBundle = new Bundle();
				            if (newNode.isExpand() != oldNode.isExpand()) {
					                diffBundle.putBoolean(KEY_IS_EXPAND, newNode.isExpand());
					            }
				            if (diffBundle.size() == 0)
				                return null;
				            return diffBundle;
				        }
			
			        // For DiffUtil, if they are the same items, whether the contents has bean changed.
			        private boolean areContentsTheSame(TreeNode oldNode, TreeNode newNode) {
				            return oldNode.getContent() != null && oldNode.getContent().equals(newNode.getContent())
				                    && oldNode.isExpand() == newNode.isExpand();
				        }
			
			        // judge if the same item for DiffUtil
			        private boolean areItemsTheSame(TreeNode oldNode, TreeNode newNode) {
				            return oldNode.getContent() != null && oldNode.getContent().equals(newNode.getContent());
				        }
			
			        /**
         * collapse all root nodes.
         */
			        public void collapseAll() {
				            // Back up the nodes are displaying.
				            List<TreeNode> temp = backupDisplayNodes();
				            //find all root nodes.
				            List<TreeNode> roots = new ArrayList<>();
				            for (TreeNode displayNode : displayNodes) {
					                if (displayNode.isRoot())
					                    roots.add(displayNode);
					            }
				            //Close all root nodes.
				            for (TreeNode root : roots) {
					                if (root.isExpand())
					                    removeChildNodes(root);
					            }
				            notifyDiff(temp);
				        }
			
			        @NonNull
			        private List<TreeNode> backupDisplayNodes() {
				            List<TreeNode> temp = new ArrayList<>();
				            for (TreeNode displayNode : displayNodes) {
					                try {
						                    temp.add(displayNode.clone());
						                } catch (CloneNotSupportedException e) {
						                    temp.add(displayNode);
						                }
					            }
				            return temp;
				        }
			
			        public void collapseNode(TreeNode pNode) {
				            List<TreeNode> temp = backupDisplayNodes();
				            removeChildNodes(pNode);
				            notifyDiff(temp);
				        }
			
			        public void collapseBrotherNode(TreeNode pNode) {
				            List<TreeNode> temp = backupDisplayNodes();
				            if (pNode.isRoot()) {
					                List<TreeNode> roots = new ArrayList<>();
					                for (TreeNode displayNode : displayNodes) {
						                    if (displayNode.isRoot())
						                        roots.add(displayNode);
						                }
					                //Close all root nodes.
					                for (TreeNode root : roots) {
						                    if (root.isExpand() && !root.equals(pNode))
						                        removeChildNodes(root);
						                }
					            } else {
					                TreeNode parent = pNode.getParent();
					                if (parent == null)
					                    return;
					                List<TreeNode> childList = parent.getChildList();
					                for (TreeNode node : childList) {
						                    if (node.equals(pNode) || !node.isExpand())
						                        continue;
						                    removeChildNodes(node);
						                }
					            }
				            notifyDiff(temp);
				        }
			
			    }
		
		
		    // Tree View Binder
		
		    public static abstract class TreeViewBinder<VH extends RecyclerView.ViewHolder> implements LayoutItemType {
			        public abstract VH provideViewHolder(View itemView);
			
			        public abstract void bindView(VH holder, int position, TreeNode node);
			
			        public static class ViewHolder extends RecyclerView.ViewHolder {
				            public ViewHolder(View rootView) {
					                super(rootView);
					            }
				
				            protected <T extends View> T findViewById(@IdRes int id) {
					                return (T) itemView.findViewById(id);
					            }
				        }
			
			    }
		
		
		    public static class FileNodeBinder extends TreeViewBinder<FileNodeBinder.ViewHolder> {
			        @Override
			        public ViewHolder provideViewHolder(View itemView) {
				            return new ViewHolder(itemView);
				        }
			
			        @Override
			        public void bindView(ViewHolder holder, int position, TreeNode node) {
				            File fileNode = (File) node.getContent();
				            if (TreeViewList.isPath) {
					                holder.tvName.setText(Uri.parse(fileNode.fileName).getLastPathSegment());
					            } else {
					                holder.tvName.setText(fileNode.fileName);
					            }
				        }
			
			        @Override
			        public int getLayoutId() {
				            return R.layout.item_file;
				        }
			
			        public class ViewHolder extends TreeViewBinder.ViewHolder {
				            public TextView tvName;
				
				            public ViewHolder(View rootView) {
					                super(rootView);
					                this.tvName = (TextView) rootView.findViewById(R.id.tv_name);
					            }
				
				        }
			    }
		
		
		    public static class DirectoryNodeBinder extends TreeViewBinder<DirectoryNodeBinder.ViewHolder> {
			        @Override
			        public ViewHolder provideViewHolder(View itemView) {
				            return new ViewHolder(itemView);
				        }
			
			        @Override
			        public void bindView(ViewHolder holder, int position, TreeNode node) {
				            holder.ivArrow.setRotation(0);
				            holder.ivArrow.setImageResource(R.drawable.arrow);
				            int rotateDegree = node.isExpand() ? 90 : 0;
				            holder.ivArrow.setRotation(rotateDegree);
				            Dir dirNode = (Dir) node.getContent();
				
				            if (TreeViewList.isPath) {
					                holder.tvName.setText(Uri.parse(dirNode.dirName).getLastPathSegment());
					            } else {
					                holder.tvName.setText(dirNode.dirName);
					            }
				
				            if (node.isLeaf())
				                holder.ivArrow.setVisibility(View.INVISIBLE);
				            else holder.ivArrow.setVisibility(View.VISIBLE);
				        }
			
			        @Override
			        public int getLayoutId() {
				            return R.layout.item_dir;
				        }
			
			        public static class ViewHolder extends TreeViewBinder.ViewHolder {
				            private ImageView ivArrow;
				            private TextView tvName;
				
				            public ViewHolder(View rootView) {
					                super(rootView);
					                this.ivArrow = (ImageView) rootView.findViewById(R.id.iv_arrow);
					                this.tvName = (TextView) rootView.findViewById(R.id.tv_name);
					            }
				
				            public ImageView getIvArrow() {
					                return ivArrow;
					            }
				
				            public TextView getTvName() {
					                return tvName;
					            }
				        }
			    }
		
		
		    public static class Dir implements TreeViewList.LayoutItemType {
			        public String dirName;
			
			        public Dir(String dirName) {
				            this.dirName = dirName;
				        }
			
			        @Override
			        public int getLayoutId() {
				            return R.layout.item_dir;
				        }
			    }
		
		
		    public static class File implements TreeViewList.LayoutItemType {
			        public String fileName;
			
			        public File(String fileName) {
				            this.fileName = fileName;
				        }
			
			        @Override
			        public int getLayoutId() {
				            return R.layout.item_file;
				        }
			    }
	}
	
}
