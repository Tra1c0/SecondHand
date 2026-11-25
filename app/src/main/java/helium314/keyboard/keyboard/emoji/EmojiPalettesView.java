/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.emoji;

import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import helium314.keyboard.event.HapticEvent;
import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.keyboard.KeyboardActionListener;
import helium314.keyboard.keyboard.KeyboardElement;
import helium314.keyboard.keyboard.KeyboardLayoutSet;
import helium314.keyboard.keyboard.KeyboardSwitcher;
import helium314.keyboard.keyboard.KeyboardView;
import helium314.keyboard.keyboard.MainKeyboardView;
import helium314.keyboard.keyboard.PointerTracker;
import helium314.keyboard.keyboard.internal.KeyDrawParams;
import helium314.keyboard.keyboard.internal.KeyVisualAttributes;
import helium314.keyboard.keyboard.internal.keyboard_parser.EmojiParserKt;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.AudioAndHapticFeedbackManager;
import helium314.keyboard.latin.dictionary.Dictionary;
import helium314.keyboard.latin.dictionary.DictionaryFactory;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.RichInputMethodManager;
import helium314.keyboard.latin.RichInputMethodSubtype;
import helium314.keyboard.latin.SingleDictionaryFacilitator;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.settings.SettingsValues;
import helium314.keyboard.latin.utils.DictionaryInfoUtils;
import helium314.keyboard.latin.utils.ResourceUtils;

import static helium314.keyboard.latin.common.Constants.NOT_A_COORDINATE;

/**
 * View class to implement Emoji palettes.
 * The Emoji keyboard consists of group of views layout/emoji_palettes_view.
 * <ol>
 * <li> Emoji category tabs.
 * <li> Delete button.
 * <li> Emoji keyboard pages that can be scrolled by swiping horizontally or by selecting a tab.
 * <li> Back to main keyboard button and enter button.
 * </ol>
 * Because of the above reasons, this class doesn't extend {@link KeyboardView}.
 */
public final class EmojiPalettesView extends LinearLayout
        implements View.OnClickListener, EmojiViewCallback {
    private static final class PagerViewHolder extends RecyclerView.ViewHolder {
        private long mCategoryId;

        private PagerViewHolder(View itemView) {
            super(itemView);
        }
    }

    /**
     * Fork, stage 93b (owner: "durchgängiger Scroll"): one page that holds every shown category one after the other
     * (ConcatAdapter of the per-category adapters), so scrolling goes on from one category into the next; the tab strip
     * jumps to a category and follows the scrolling. No sideways paging any more.
     */
    private final class PagerAdapter extends RecyclerView.Adapter<PagerViewHolder> {
        private final Map<Integer, RecyclerView> mViews = new HashMap<>(1);
        private RecyclerView mList;
        private EmojiPalettesAdapter mRecentsAdapter;
        // stage 97: heading above and divider below the recent emojis, shown only while there are recent emojis
        private RecentsMarkAdapter mRecentsHeading;
        private RecentsMarkAdapter mRecentsDivider;
        /** A tab moved the list: the tab stays marked until the user scrolls himself (review M2: a short last category). */
        private boolean mJumped;

        private PagerAdapter(ViewPager2 pager) {
            setHasStableIds(true);
            pager.setUserInputEnabled(false); // a single page: nothing to swipe to
        }

        /**
         * Positions in the continuous list: the pages of the shown categories one after the other, and (stage 97) the heading and
         * the divider of the recent emojis around their pages while they are shown, see EmojiListPositions.
         */
        private EmojiListPositions positions() {
            final java.util.List<EmojiCategory.CategoryProperties> shown = mEmojiCategory.getShownCategories();
            final int[] pageCounts = new int[shown.size()];
            int recentsIndex = -1;
            for (int i = 0; i < shown.size(); i++) {
                pageCounts[i] = shown.get(i).getPageCount();
                if (shown.get(i).getCategory() == EmojiCategory.Category.RECENTS) recentsIndex = i;
            }
            return new EmojiListPositions(pageCounts, recentsIndex, mRecentsHeading != null && mRecentsHeading.getVisible());
        }

        private int indexOf(EmojiCategory.Category category) {
            final java.util.List<EmojiCategory.CategoryProperties> shown = mEmojiCategory.getShownCategories();
            for (int i = 0; i < shown.size(); i++) if (shown.get(i).getCategory() == category) return i;
            return -1;
        }

        /** The category and the page within it of adapter position [position]. */
        int[] categoryAt(int position) {
            final int[] at = positions().at(position);
            if (at == null) return new int[] { mEmojiCategory.getCurrentCategory().ordinal(), 0 };
            return new int[] { mEmojiCategory.getShownCategories().get(at[0]).getCategory().ordinal(), at[1] };
        }

        void scrollTo(EmojiCategory.Category category, int page) {
            if (mList == null) return;
            mJumped = true;
            final int index = indexOf(category);
            // page 0 of the recent emojis starts with their heading (stage 97)
            final int target = index < 0 ? 0 : positions().scrollTarget(index, page);
            ((LinearLayoutManager) mList.getLayoutManager()).scrollToPositionWithOffset(target, 0);
        }

        private boolean hasRecents() {
            return !getRecentsKeyboard().getSortedKeys().isEmpty();
        }

        void recentsChanged() {
            if (mRecentsAdapter != null) mRecentsAdapter.notifyItemRangeChanged(0, mRecentsAdapter.getItemCount());
            // stage 97: the first recent emoji brings the heading and the divider, clearing them takes them away
            final boolean shown = hasRecents();
            if (mRecentsHeading != null) mRecentsHeading.updateVisible(shown);
            if (mRecentsDivider != null) mRecentsDivider.updateVisible(shown);
        }

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            recyclerView.setItemViewCacheSize(mEmojiCategory.getShownCategories().size());
        }

        @NonNull
        @Override
        public PagerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            var view = LayoutInflater.from(parent.getContext()).inflate(R.layout.emoji_category_view, parent, false);
            var viewHolder = new PagerViewHolder(view);
            var emojiRecyclerView = getRecyclerView(view);

            emojiRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) mJumped = false;
                }

                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateState(recyclerView);
                }
            });

            emojiRecyclerView.setPersistentDrawingCache(PERSISTENT_NO_CACHE);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(PagerViewHolder holder, int position) {
            holder.mCategoryId = 0;
            var recyclerView = getRecyclerView(holder.itemView);
            mViews.put(position, recyclerView);
            mList = recyclerView;
            final var parts = new java.util.ArrayList<RecyclerView.Adapter<? extends RecyclerView.ViewHolder>>();
            mRecentsAdapter = null;
            mRecentsHeading = null;
            mRecentsDivider = null;
            final boolean recents = hasRecents();
            for (EmojiCategory.CategoryProperties properties : mEmojiCategory.getShownCategories()) {
                final var adapter = new EmojiPalettesAdapter(mEmojiCategory, properties.getCategory(), EmojiPalettesView.this);
                if (properties.getCategory() == EmojiCategory.Category.RECENTS) {
                    // stage 97: "Zuletzt benutzt" above the recent emojis and a divider below them (positions: EmojiListPositions)
                    mRecentsAdapter = adapter;
                    mRecentsHeading = new RecentsMarkAdapter(true, recents);
                    mRecentsDivider = new RecentsMarkAdapter(false, recents);
                    parts.add(mRecentsHeading);
                    parts.add(adapter);
                    parts.add(mRecentsDivider);
                } else parts.add(adapter);
            }
            // one view type for all categories: the page views are shared while scrolling through them (heading and divider have
            // view types of their own)
            recyclerView.setAdapter(new androidx.recyclerview.widget.ConcatAdapter(
                    new androidx.recyclerview.widget.ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(), parts));
            recyclerView.setItemViewCacheSize(4);

            // the list starts (and after a rebuild, e.g. clearKeyboardCache, goes back) where the user was
            scrollTo(mEmojiCategory.getCurrentCategory(), mEmojiCategory.getCurrentCategoryPageId());
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        @Override
        public void onViewDetachedFromWindow(PagerViewHolder holder) {
            // the recent emojis are put in order when the user leaves them (see updateState), and here when the palette closes
            getRecentsKeyboard().flushPendingRecentKeys();
            recentsChanged();
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        private static RecyclerView getRecyclerView(View view) {
            return view.findViewById(R.id.emoji_keyboard_list);
        }

        private void updateState(@NonNull RecyclerView recyclerView) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            final int firstVisible = layoutManager.findFirstVisibleItemPosition();
            if (firstVisible == RecyclerView.NO_POSITION) return;
            final int[] at = categoryAt(firstVisible);
            final EmojiCategory.Category category = EmojiCategory.Category.getEntries().get(at[0]);
            if (mJumped) return; // the tab the user chose stays, even where the list cannot scroll that far
            if (category != mEmojiCategory.getCurrentCategory()) {
                final boolean leavingRecents = mEmojiCategory.isInRecentTab();
                setCurrentCategoryFromScroll(category);
                if (leavingRecents) { // out of sight now: put the recent emojis in order
                    getRecentsKeyboard().flushPendingRecentKeys();
                    recentsChanged();
                }
            }
            if (at[1] != mEmojiCategory.getCurrentCategoryPageId()) mEmojiCategory.setCurrentCategoryPageId(at[1]); // review L4: not on every pixel
            // the indicator shows where in the current category the list is
            final View first = layoutManager.findViewByPosition(firstVisible);
            final float within = first == null || first.getHeight() == 0 ? 0f : Math.min(1f, -first.getTop() / (float) first.getHeight());
            mEmojiCategoryPageIndicatorView.setCategoryPageId(mEmojiCategory.getCurrentCategoryPageCount(), at[1], within);
        }
    }

    private static SingleDictionaryFacilitator sDictionaryFacilitator;

    private boolean initialized = false;
    private final Colors mColors;
    private final EmojiLayoutParams mEmojiLayoutParams;
    private LinearLayout mTabStrip;
    private EmojiCategoryPageIndicatorView mEmojiCategoryPageIndicatorView;
    private KeyboardActionListener mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;
    private final EmojiCategory mEmojiCategory;
    private ViewPager2 mPager;
    // fork, stage 25a: mixer mode behind the "Emoji-Mixer" key of the bottom row
    private helium314.keyboard.agent.EmojiMixPalette mMixer;

    public EmojiPalettesView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.emojiPalettesViewStyle);
    }

    public EmojiPalettesView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        mColors = Settings.getValues().mColors;
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(context, null, null);
        final Resources res = context.getResources();
        mEmojiLayoutParams = new EmojiLayoutParams(res);
        builder.setSubtype(RichInputMethodSubtype.Companion.getEmojiSubtype());
        builder.setKeyboardGeometry(ResourceUtils.getKeyboardWidth(context, Settings.getValues()),
                mEmojiLayoutParams.getEmojiKeyboardHeight());
        final KeyboardLayoutSet layoutSet = builder.build();
        final TypedArray emojiPalettesViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.EmojiPalettesView, defStyle, R.style.EmojiPalettesView);
        mEmojiCategory = new EmojiCategory(context, layoutSet, emojiPalettesViewAttr);
        emojiPalettesViewAttr.recycle();
        setFitsSystemWindows(true);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (mMixer != null) mMixer.beforeMeasure(widthMeasureSpec); // the pages give up the mixer bar's height
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final Resources res = getContext().getResources();
        // The main keyboard expands to the entire this {@link KeyboardView}.
        final int width = ResourceUtils.getKeyboardWidth(getContext(), Settings.getValues())
                + getPaddingLeft() + getPaddingRight();
        final int height = ResourceUtils.getSecondaryKeyboardHeight(res, Settings.getValues())
                + getPaddingTop() + getPaddingBottom();
        mEmojiCategoryPageIndicatorView.mWidth = width;
        setMeasuredDimension(width, height);
    }

    private void addTab(LinearLayout host, EmojiCategory.Category category) {
        final ImageView iconView = new ImageView(getContext());
        mColors.setBackground(iconView, ColorType.STRIP_BACKGROUND);
        mColors.setColor(iconView, ColorType.EMOJI_CATEGORY);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setImageResource(mEmojiCategory.getCategoryTabIcon(category));
        iconView.setContentDescription(mEmojiCategory.getAccessibilityDescription(category));
        iconView.setTag(category);
        host.addView(iconView);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        iconView.setOnClickListener(this);
        if (category == EmojiCategory.Category.RECENTS) {
            iconView.setOnLongClickListener(v -> {
                AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_LONG_PRESS);
                clearRecentKeys();

                return true;
            });
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void initialize() { // needs to be delayed for access to EmojiTabStrip, which is not a child of this view
        if (initialized) return;
        mEmojiCategory.initialize();
        mTabStrip = (LinearLayout) KeyboardSwitcher.getInstance().getEmojiTabStrip();
        if (Settings.getValues().isSecondaryStripVisible()) {
            for (EmojiCategory.CategoryProperties properties : mEmojiCategory.getShownCategories()) {
                addTab(mTabStrip, properties.getCategory());
            }
        }

        mPager = findViewById(R.id.emoji_pager);
        mPager.setAdapter(new PagerAdapter(mPager));
        mEmojiLayoutParams.setEmojiListProperties(mPager);
        mEmojiCategoryPageIndicatorView = findViewById(R.id.emoji_category_page_id_view);
        mEmojiLayoutParams.setCategoryPageIdViewProperties(mEmojiCategoryPageIndicatorView);
        setCurrentCategory(mEmojiCategory.getCurrentCategory(), true);
        mEmojiCategoryPageIndicatorView.setColors(mColors.get(ColorType.EMOJI_CATEGORY_SELECTED), mColors.get(ColorType.STRIP_BACKGROUND));
        mMixer = new helium314.keyboard.agent.EmojiMixPalette(this, mPager);
        initialized = true;
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through {@link android.view.View.OnClickListener}
     * interface to handle non-canceled touch-up events from View-based elements such as the space
     * bar.
     */
    @Override
    public void onClick(View v) {
        final Object tag = v.getTag();
        if (tag instanceof EmojiCategory.Category category) {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS);
            if (category != mEmojiCategory.getCurrentCategory()) {
                setCurrentCategory(category, false);
                updateEmojiCategoryPageIdView();
            }
        }
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through {@link EmojiViewCallback}
     * interface to handle touch events from non-View-based elements such as Emoji buttons.
     */
    @Override
    public void onPressKey(final Key key) {
        final int code = key.getCode();
        mKeyboardActionListener.onPressKey(code, 0, 1, HapticEvent.KEY_PRESS);
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through {@link EmojiViewCallback}
     * interface to handle touch events from non-View-based elements such as Emoji buttons.
     * This may be called without any prior call to {@link EmojiViewCallback#onPressKey(Key)}.
     */
    @Override
    public void onReleaseKey(final Key key) {
        final int code = key.getCode();
        // in mixer mode a tapped emoji is chosen for the mix, not typed
        if (mMixer != null && mMixer.isActive() && (code > 0 || code == KeyCode.MULTIPLE_CODE_POINTS)) {
            final String emoji = code > 0 ? new String(Character.toChars(code)) : key.getOutputText();
            if (emoji != null && mMixer.onEmoji(emoji)) {
                mKeyboardActionListener.onReleaseKey(code, false);
                return;
            }
        }
        addRecentKey(key);
        if (code == KeyCode.MULTIPLE_CODE_POINTS) {
            // todo: when we enter some emoticons, e.g. :-D, inline emoji search is triggered and emoji view is closed
            //  (one more instance of "emoji search should recognize emoticons", but in this case could be fixed in other ways)
            mKeyboardActionListener.onTextInput(key.getOutputText());
        } else if (code != KeyCode.UNSPECIFIED) {
            mKeyboardActionListener.onCodeInput(code, NOT_A_COORDINATE, NOT_A_COORDINATE, false);
        }
        mKeyboardActionListener.onReleaseKey(code, false);
        if (Settings.getValues().mAlphaAfterEmojiInEmojiView)
            mKeyboardActionListener.onCodeInput(KeyCode.ALPHA, NOT_A_COORDINATE, NOT_A_COORDINATE, false);
    }

    @Override
    public void onRemoveRecentsKey(Key key)
    {
        getRecentsKeyboard().removeRecentsKey(key);
    }

    private void clearRecentKeys() {
        if (RecentEmojis.get().isEmpty()) return;
        RecentEmojis.clear();
        getRecentsKeyboard().removeAllKeys();
        ((PagerAdapter) mPager.getAdapter()).recentsChanged();
        KeyboardSwitcher.getInstance().showToast(getResources().getString(R.string.recent_emojis_cleared), true);
    }

    @Override
    public String getDescription(String emoji) {
        if (sDictionaryFacilitator == null) {
            return null;
        }

        var wordProperty = sDictionaryFacilitator.getWordProperty(EmojiParserKt.getEmojiNeutralVersion(emoji));
        if (wordProperty == null || ! wordProperty.mHasShortcuts) {
            return null;
        }

        return wordProperty.mShortcutTargets.get(0).mWord;
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        if (!enabled) return;
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void startEmojiPalettes(final KeyVisualAttributes keyVisualAttr,
               final EditorInfo editorInfo, final KeyboardActionListener keyboardActionListener) {
        initialize();

        setupBottomRowKeyboard(editorInfo, keyboardActionListener);
        mMixer.start(editorInfo);
        final KeyDrawParams params = new KeyDrawParams();
        params.updateParams(mEmojiLayoutParams.getBottomRowKeyboardHeight(), keyVisualAttr);
        new EmojiLayoutParams(getResources()).setEmojiListProperties(mPager); // necessary when floating
        mEmojiCategory.reloadRecents(); // in case recents changed from outside emoji keyboards
        setupSidePadding();
        initDictionaryFacilitator();
    }

    private void addRecentKey(Key key) {
        if (Settings.getValues().mIncognitoModeEnabled) {
            // We do not want to log recent keys while being in incognito
            return;
        }
        if (mEmojiCategory.isInRecentTab()) {
            getRecentsKeyboard().addPendingKey(key);
            return;
        }
        getRecentsKeyboard().addKeyFirst(key);
        ((PagerAdapter) mPager.getAdapter()).recentsChanged();
    }

    private void setupBottomRowKeyboard(EditorInfo editorInfo, KeyboardActionListener keyboardActionListener) {
        MainKeyboardView keyboardView = findViewById(R.id.bottom_row_keyboard);
        keyboardView.setKeyboardActionListener(keyboardActionListener);
        PointerTracker.switchTo(keyboardView);
        KeyboardLayoutSet kls = KeyboardLayoutSet.Builder.Companion.buildEmojiClipBottomRow(getContext(), editorInfo);
        Keyboard keyboard = kls.getKeyboard(KeyboardElement.EMOJI_BOTTOM_ROW);
        keyboardView.setKeyboard(keyboard);
    }

    private void setupSidePadding() {
        final SettingsValues sv = Settings.getValues();
        final int keyboardWidth = ResourceUtils.getKeyboardWidth(getContext(), sv);
        final TypedArray keyboardAttr = getContext().obtainStyledAttributes(
                null, R.styleable.Keyboard, R.attr.keyboardStyle, R.style.Keyboard);
        final float leftPadding = keyboardAttr.getFraction(R.styleable.Keyboard_keyboardLeftPadding,
                keyboardWidth, keyboardWidth, 0f) * sv.mSidePaddingScale;
        final float rightPadding =  keyboardAttr.getFraction(R.styleable.Keyboard_keyboardRightPadding,
                keyboardWidth, keyboardWidth, 0f) * sv.mSidePaddingScale;
        keyboardAttr.recycle();
        mPager.setPadding(
                (int) leftPadding,
                mPager.getPaddingTop(),
                (int) rightPadding,
                mPager.getPaddingBottom()
        );
        mEmojiCategoryPageIndicatorView.setPadding(
                (int) leftPadding,
                mEmojiCategoryPageIndicatorView.getPaddingTop(),
                (int) rightPadding,
                mEmojiCategoryPageIndicatorView.getPaddingBottom()
        );
        // setting width does not do anything, so we have some workaround in EmojiCategoryPageIndicatorView
    }

    public void stopEmojiPalettes() {
        if (!initialized) return;
        mMixer.stop(); // leaving the palette clears the mixer's choice
        getRecentsKeyboard().flushPendingRecentKeys();
    }

    private DynamicGridKeyboard getRecentsKeyboard() {
        return mEmojiCategory.getKeyboard(EmojiCategory.Category.RECENTS, 0);
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
    }

    private void updateEmojiCategoryPageIdView() {
        if (mEmojiCategoryPageIndicatorView == null) {
            return;
        }
        mEmojiCategoryPageIndicatorView.setCategoryPageId(
                mEmojiCategory.getCurrentCategoryPageCount(),
                mEmojiCategory.getCurrentCategoryPageId(), 0.0f);
    }

    private void setCurrentCategory(EmojiCategory.Category category, boolean initial) {
        EmojiCategory.Category oldCategory = mEmojiCategory.getCurrentCategory();
        if (initial || oldCategory != category) {
            mEmojiCategory.setCurrentCategory(category);

            // stage 93b: a tab (or the start) moves the continuous list; scrolling calls setCurrentCategoryFromScroll instead
            if (oldCategory == EmojiCategory.Category.RECENTS && !initial && mPager.getAdapter() instanceof PagerAdapter) {
                // review M1: leaving the recents by a tab puts them in order, as leaving them by scrolling does
                getRecentsKeyboard().flushPendingRecentKeys();
                ((PagerAdapter) mPager.getAdapter()).recentsChanged();
            }
            if (mPager.getAdapter() instanceof PagerAdapter)
                ((PagerAdapter) mPager.getAdapter()).scrollTo(category, initial ? mEmojiCategory.getCurrentCategoryPageId() : 0);
            highlightTab(oldCategory, category);
        }
    }

    /** Stage 93b: the list scrolled into [category]: mark its tab, do not move the list. */
    private void setCurrentCategoryFromScroll(EmojiCategory.Category category) {
        EmojiCategory.Category oldCategory = mEmojiCategory.getCurrentCategory();
        if (oldCategory == category) return;
        mEmojiCategory.setCurrentCategory(category);
        highlightTab(oldCategory, category);
    }

    private void highlightTab(EmojiCategory.Category oldCategory, EmojiCategory.Category category) {
        {

            if (Settings.getValues().isSecondaryStripVisible()) {
                View old = mTabStrip.findViewWithTag(oldCategory);
                View current = mTabStrip.findViewWithTag(category);

                if (old instanceof ImageView)
                    Settings.getValues().mColors.setColor((ImageView) old, ColorType.EMOJI_CATEGORY);
                if (current instanceof ImageView)
                    Settings.getValues().mColors.setColor((ImageView) current, ColorType.EMOJI_CATEGORY_SELECTED);
            }
        }
    }

    public void clearKeyboardCache() {
        if (!initialized) {
            return;
        }

        mEmojiCategory.clearKeyboardCache();
        mPager.getAdapter().notifyDataSetChanged();
        closeDictionaryFacilitator();
    }

    private void initDictionaryFacilitator() {
        if (Settings.getValues().mShowEmojiDescriptions) {
            var locale = RichInputMethodManager.getInstance().getCurrentSubtype().getLocale();
            if (sDictionaryFacilitator == null || ! sDictionaryFacilitator.isForLocale(locale)) {
                closeDictionaryFacilitator();
                var dictFile = DictionaryInfoUtils.getCachedDictForLocaleAndType(locale, Dictionary.TYPE_EMOJI, getContext());
                var dictionary = dictFile != null? DictionaryFactory.getDictionary(dictFile, locale) : null;
                sDictionaryFacilitator = dictionary != null? new SingleDictionaryFacilitator(dictionary) : null;
            }
        } else {
            closeDictionaryFacilitator();
        }
    }

    public static void closeDictionaryFacilitator() {
        if (sDictionaryFacilitator != null) {
            sDictionaryFacilitator.closeDictionaries();
            sDictionaryFacilitator = null;
        }
    }
}
