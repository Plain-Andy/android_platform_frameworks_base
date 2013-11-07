/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.app;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

/**
 * Media route chooser dialog fragment.
 * <p>
 * Creates a {@link MediaRouteChooserDialog}.  The application may subclass
 * this dialog fragment to customize the media route chooser dialog.
 * </p>
 *
 * TODO: Move this back into the API, as in the support library media router.
 */
public class MediaRouteChooserDialogFragment extends DialogFragment {
    private final String ARGUMENT_ROUTE_TYPES = "routeTypes";

    private View.OnClickListener mExtendedSettingsClickListener;

    /**
     * Creates a media route chooser dialog fragment.
     * <p>
     * All subclasses of this class must also possess a default constructor.
     * </p>
     */
    public MediaRouteChooserDialogFragment() {
        setCancelable(true);
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Dialog);
    }

    public int getRouteTypes() {
        Bundle args = getArguments();
        return args != null ? args.getInt(ARGUMENT_ROUTE_TYPES) : 0;
    }

    public void setRouteTypes(int types) {
        if (types != getRouteTypes()) {
            Bundle args = getArguments();
            if (args == null) {
                args = new Bundle();
            }
            args.putInt(ARGUMENT_ROUTE_TYPES, types);
            setArguments(args);

            notifyDataSetChanged();
            if (mListView != null && mSelectedItemPosition >= 0) {
                mListView.setItemChecked(mSelectedItemPosition, true);
            }
        }

        void scrollToEditingGroup() {
            if (mCategoryEditingGroups == null || mListView == null) return;

            int pos = 0;
            int bound = 0;
            final int itemCount = mItems.size();
            for (int i = 0; i < itemCount; i++) {
                final Object item = mItems.get(i);
                if (item != null && item == mCategoryEditingGroups) {
                    bound = i;
                }
                if (item == null) {
                    pos = i;
                    break; // this is always below the category header; we can stop here.
                }
            }

            mListView.smoothScrollToPosition(pos, bound);
        }

        void scrollToSelectedItem() {
            if (mListView == null || mSelectedItemPosition < 0) return;

            mListView.smoothScrollToPosition(mSelectedItemPosition);
        }

        void addSelectableRoutes(RouteInfo selectedRoute, List<RouteInfo> from) {
            final int routeCount = from.size();
            for (int j = 0; j < routeCount; j++) {
                final RouteInfo info = from.get(j);
                if (info == selectedRoute) {
                    mSelectedItemPosition = mItems.size();
                }
                mItems.add(info);
            }
        }

        void addGroupEditingCategoryRoutes(List<RouteInfo> from) {
            // Unpack groups and flatten for presentation
            // mSortRouteList will always be empty here.
            final int topCount = from.size();
            for (int i = 0; i < topCount; i++) {
                final RouteInfo route = from.get(i);
                final RouteGroup group = route.getGroup();
                if (group == route) {
                    // This is a group, unpack it.
                    final int groupCount = group.getRouteCount();
                    for (int j = 0; j < groupCount; j++) {
                        final RouteInfo innerRoute = group.getRouteAt(j);
                        mSortRouteList.add(innerRoute);
                    }
                } else {
                    mSortRouteList.add(route);
                }
            }
            // Sort by name. This will keep the route positions relatively stable even though they
            // will be repeatedly added and removed.
            Collections.sort(mSortRouteList, mComparator);

            mItems.addAll(mSortRouteList);
            mSortRouteList.clear();

            mItems.add(null); // Sentinel reserving space for the "done" button.
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public int getViewTypeCount() {
            return 5;
        }

        @Override
        public int getItemViewType(int position) {
            final Object item = getItem(position);
            if (item instanceof RouteCategory) {
                return position == 0 ? VIEW_TOP_HEADER : VIEW_SECTION_HEADER;
            } else if (item == null) {
                return VIEW_GROUPING_DONE;
            } else {
                final RouteInfo info = (RouteInfo) item;
                if (info.getCategory() == mCategoryEditingGroups) {
                    return VIEW_GROUPING_ROUTE;
                }
                return VIEW_ROUTE;
            }
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            switch (getItemViewType(position)) {
                case VIEW_ROUTE:
                    return ((RouteInfo) mItems.get(position)).isEnabled();
                case VIEW_GROUPING_ROUTE:
                case VIEW_GROUPING_DONE:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public Object getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final int viewType = getItemViewType(position);

            ViewHolder holder;
            if (convertView == null) {
                convertView = mInflater.inflate(ITEM_LAYOUTS[viewType], parent, false);
                holder = new ViewHolder();
                holder.position = position;
                holder.text1 = (TextView) convertView.findViewById(R.id.text1);
                holder.text2 = (TextView) convertView.findViewById(R.id.text2);
                holder.icon = (ImageView) convertView.findViewById(R.id.icon);
                holder.check = (CheckBox) convertView.findViewById(R.id.check);
                holder.expandGroupButton = (ImageButton) convertView.findViewById(
                        R.id.expand_button);
                if (holder.expandGroupButton != null) {
                    holder.expandGroupListener = new ExpandGroupListener();
                    holder.expandGroupButton.setOnClickListener(holder.expandGroupListener);
                }

                final View fview = convertView;
                final ListView list = (ListView) parent;
                final ViewHolder fholder = holder;
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        list.performItemClick(fview, fholder.position, 0);
                    }
                });
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
                holder.position = position;
            }

            switch (viewType) {
                case VIEW_ROUTE:
                case VIEW_GROUPING_ROUTE:
                    bindItemView(position, holder);
                    break;
                case VIEW_SECTION_HEADER:
                case VIEW_TOP_HEADER:
                    bindHeaderView(position, holder);
                    break;
            }

            convertView.setActivated(position == mSelectedItemPosition);
            convertView.setEnabled(isEnabled(position));

            return convertView;
        }

        void bindItemView(int position, ViewHolder holder) {
            RouteInfo info = (RouteInfo) mItems.get(position);
            holder.text1.setText(info.getName(getActivity()));
            final CharSequence status = info.getStatus();
            if (TextUtils.isEmpty(status)) {
                holder.text2.setVisibility(View.GONE);
            } else {
                holder.text2.setVisibility(View.VISIBLE);
                holder.text2.setText(status);
            }
            Drawable icon = info.getIconDrawable();
            if (icon != null) {
                // Make sure we have a fresh drawable where it doesn't matter if we mutate it
                icon = icon.getConstantState().newDrawable(getResources());
            }
            holder.icon.setImageDrawable(icon);
            holder.icon.setVisibility(icon != null ? View.VISIBLE : View.GONE);

            RouteCategory cat = info.getCategory();
            boolean canGroup = false;
            if (cat == mCategoryEditingGroups) {
                RouteGroup group = info.getGroup();
                holder.check.setEnabled(group.getRouteCount() > 1);
                holder.check.setChecked(group == mEditingGroup);
            } else {
                if (cat.isGroupable()) {
                    final RouteGroup group = (RouteGroup) info;
                    canGroup = group.getRouteCount() > 1 ||
                            getItemViewType(position - 1) == VIEW_ROUTE ||
                            (position < getCount() - 1 &&
                                    getItemViewType(position + 1) == VIEW_ROUTE);
                }
            }

            if (holder.expandGroupButton != null) {
                holder.expandGroupButton.setVisibility(canGroup ? View.VISIBLE : View.GONE);
                holder.expandGroupListener.position = position;
            }
        }

        void bindHeaderView(int position, ViewHolder holder) {
            RouteCategory cat = (RouteCategory) mItems.get(position);
            holder.text1.setText(cat.getName(getActivity()));
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final int type = getItemViewType(position);
            if (type == VIEW_SECTION_HEADER || type == VIEW_TOP_HEADER) {
                return;
            } else if (type == VIEW_GROUPING_DONE) {
                finishGrouping();
                return;
            } else {
                final Object item = getItem(position);
                if (!(item instanceof RouteInfo)) {
                    // Oops. Stale event running around? Skip it.
                    return;
                }

                final RouteInfo route = (RouteInfo) item;
                if (type == VIEW_ROUTE) {
                    mRouter.selectRouteInt(mRouteTypes, route, true);
                    dismiss();
                } else if (type == VIEW_GROUPING_ROUTE) {
                    final Checkable c = (Checkable) view;
                    final boolean wasChecked = c.isChecked();

                    mIgnoreUpdates = true;
                    RouteGroup oldGroup = route.getGroup();
                    if (!wasChecked && oldGroup != mEditingGroup) {
                        // Assumption: in a groupable category oldGroup will never be null.
                        if (mRouter.getSelectedRoute(mRouteTypes) == oldGroup) {
                            // Old group was selected but is now empty. Select the group
                            // we're manipulating since that's where the last route went.
                            mRouter.selectRouteInt(mRouteTypes, mEditingGroup, true);
                        }
                        oldGroup.removeRoute(route);
                        mEditingGroup.addRoute(route);
                        c.setChecked(true);
                    } else if (wasChecked && mEditingGroup.getRouteCount() > 1) {
                        mEditingGroup.removeRoute(route);

                        // In a groupable category this will add
                        // the route into its own new group.
                        mRouter.addRouteInt(route);
                    }
                    mIgnoreUpdates = false;
                    update();
                }
            }
        }

        boolean isGrouping() {
            return mCategoryEditingGroups != null;
        }

        void finishGrouping() {
            mCategoryEditingGroups = null;
            mEditingGroup = null;
            getDialog().setCanceledOnTouchOutside(true);
            update();
            scrollToSelectedItem();
        }

        class ExpandGroupListener implements View.OnClickListener {
            int position;

            @Override
            public void onClick(View v) {
                // Assumption: this is only available for the user to click if we're presenting
                // a groupable category, where every top-level route in the category is a group.
                final RouteGroup group = (RouteGroup) getItem(position);
                mEditingGroup = group;
                mCategoryEditingGroups = group.getCategory();
                getDialog().setCanceledOnTouchOutside(false);
                mRouter.selectRouteInt(mRouteTypes, mEditingGroup, true);
                update();
                scrollToEditingGroup();
            }
        }
    }

    class MediaRouterCallback extends MediaRouter.Callback {
        @Override
        public void onRouteSelected(MediaRouter router, int type, RouteInfo info) {
            mAdapter.update();
            updateVolume();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, int type, RouteInfo info) {
            mAdapter.update();
        }

        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo info) {
            mAdapter.update();
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo info) {
            if (info == mAdapter.mEditingGroup) {
                mAdapter.finishGrouping();
            }
            mAdapter.update();
        }

        @Override
        public void onRouteChanged(MediaRouter router, RouteInfo info) {
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onRouteGrouped(MediaRouter router, RouteInfo info,
                RouteGroup group, int index) {
            mAdapter.update();
        }

        @Override
        public void onRouteUngrouped(MediaRouter router, RouteInfo info, RouteGroup group) {
            mAdapter.update();
        }

        @Override
        public void onRouteVolumeChanged(MediaRouter router, RouteInfo info) {
            if (!mIgnoreCallbackVolumeChanges) {
                updateVolume();
            }
        }
    }

    public void setExtendedSettingsClickListener(View.OnClickListener listener) {
        if (listener != mExtendedSettingsClickListener) {
            mExtendedSettingsClickListener = listener;

            MediaRouteChooserDialog dialog = (MediaRouteChooserDialog)getDialog();
            if (dialog != null) {
                dialog.setExtendedSettingsClickListener(listener);
            }
        }
    }

    /**
     * Called when the chooser dialog is being created.
     * <p>
     * Subclasses may override this method to customize the dialog.
     * </p>
     */
    public MediaRouteChooserDialog onCreateChooserDialog(
            Context context, Bundle savedInstanceState) {
        return new MediaRouteChooserDialog(context, getTheme());
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MediaRouteChooserDialog dialog = onCreateChooserDialog(getActivity(), savedInstanceState);
        dialog.setRouteTypes(getRouteTypes());
        dialog.setExtendedSettingsClickListener(mExtendedSettingsClickListener);
        return dialog;
    }
}
