/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2015 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import org.connectbot.bean.HostBean;
import org.connectbot.transport.SSH;
import org.connectbot.transport.Telnet;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.HostDatabase;
import org.connectbot.views.CheckableMenuItem;

public class HostEditorFragment extends Fragment {

	private static final String ARG_EXISTING_HOST_ID = "existingHostId";
	private static final String ARG_EXISTING_HOST = "existingHost";
	private static final String ARG_IS_EXPANDED = "isExpanded";
	private static final String ARG_PUBKEY_NAMES = "pubkeyNames";
	private static final String ARG_PUBKEY_VALUES = "pubkeyValues";
	private static final String ARG_QUICKCONNECT_STRING = "quickConnectString";

	// Note: The "max" value for mFontSizeSeekBar is 32. If these font values change, this value
	// must be changed in the SeekBar's XML.
	private static final int MINIMUM_FONT_SIZE = 8;
	private static final int MAXIMUM_FONT_SIZE = 40;

	// The host being edited.
	private HostBean mHost;

	// The pubkey lists (names and values). Note that these are declared as ArrayLists rather than
	// Lists because Bundles can only contain ArrayLists, not general Lists.
	private ArrayList<String> mPubkeyNames;
	private ArrayList<String> mPubkeyValues;

	// The listener for changes to this host.
	private Listener mListener;

	// Whether the URI parts subsection is expanded.
	private boolean mIsUriEditorExpanded = false;

	// Whether a URI text edit is in progress. When the quick-connect field is being edited, changes
	// automatically propagate to the URI part fields; likewise, when the URI part fields are
	// edited, changes are propagated to the quick-connect field. This boolean safeguards against
	// infinite loops which can be caused by one field changing the other field, which changes the
	// first field, etc.
	private boolean mUriFieldEditInProgress = false;

	// Names and values for the colors displayed in the color Spinner. Names are localized, while
	// values are the same across languages, so the values are the ones saved to the database.
	private TypedArray mColorNames;
	private TypedArray mColorValues;

	// Likewise, but for SSH auth agent.
	private TypedArray mSshAuthNames;
	private TypedArray mSshAuthValues;

	// Likewise, but for DEL key.
	private TypedArray mDelKeyNames;
	private TypedArray mDelKeyValues;

	// A map from Charset display name to Charset value (i.e., unique ID for the Charset).
	private Map<String, String> mCharsetData;

	private View mTransportItem;
	private TextView mTransportText;
	private TextInputLayout mQuickConnectContainer;
	private EditText mQuickConnectField;
	private ImageButton mExpandCollapseButton;
	private View mUriPartsContainer;
	private View mUsernameContainer;
	private EditText mUsernameField;
	private View mHostnameContainer;
	private EditText mHostnameField;
	private View mPortContainer;
	private EditText mPortField;
	private View mNicknameItem;
	private EditText mNicknameField;
	private View mColorItem;
	private TextView mColorText;
	private EditText mFontSizeText;
	private SeekBar mFontSizeSeekBar;
	private View mPubkeyItem;
	private TextView mPubkeyText;
	private View mDelKeyItem;
	private TextView mDelKeyText;
	private View mEncodingItem;
	private TextView mEncodingText;
	private View mUseSshAuthItem;
	private SwitchCompat mUseSshAuthSwitch;
	private View mUseSshConfirmationItem;
	private AppCompatCheckBox mUseSshConfirmationCheckbox;
	private View mCompressionItem;
	private CheckableMenuItem mCompressionSwitch;
	private View mStartShellItem;
	private CheckableMenuItem mStartShellSwitch;
	private View mStayConnectedItem;
	private CheckableMenuItem mStayConnectedSwitch;
	private View mCloseOnDisconnectItem;
	private CheckableMenuItem mCloseOnDisconnectSwitch;
	private EditText mPostLoginAutomationField;

	public static HostEditorFragment newInstance(
			HostBean existingHost, ArrayList<String> pubkeyNames, ArrayList<String> pubkeyValues) {
		HostEditorFragment fragment = new HostEditorFragment();
		Bundle args = new Bundle();
		if (existingHost != null) {
			args.putLong(ARG_EXISTING_HOST_ID, existingHost.getId());
			args.putParcelable(ARG_EXISTING_HOST, existingHost.getValues());
		}
		args.putStringArrayList(ARG_PUBKEY_NAMES, pubkeyNames);
		args.putStringArrayList(ARG_PUBKEY_VALUES, pubkeyValues);
		fragment.setArguments(args);
		return fragment;
	}

	public HostEditorFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle bundle = savedInstanceState == null ? getArguments() : savedInstanceState;

		Parcelable existingHostParcelable = bundle.getParcelable(ARG_EXISTING_HOST);
		if (existingHostParcelable != null) {
			mHost = HostBean.fromContentValues((ContentValues) existingHostParcelable);
			mHost.setId(bundle.getLong(ARG_EXISTING_HOST_ID));
		} else {
			mHost = new HostBean();
		}

		mPubkeyNames = bundle.getStringArrayList(ARG_PUBKEY_NAMES);
		mPubkeyValues = bundle.getStringArrayList(ARG_PUBKEY_VALUES);

		mIsUriEditorExpanded = bundle.getBoolean(ARG_IS_EXPANDED);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_host_editor, container, false);

		mTransportItem = view.findViewById(R.id.protocol_item);
		mTransportItem.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				PopupMenu menu = new PopupMenu(getActivity(), v);
				for (String name : TransportFactory.getTransportNames()) {
					menu.getMenu().add(name);
				}
				menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						setTransportType(
								item.getTitle().toString(), /* setDefaultPortInModel */ true);
						return true;
					}
				});
				menu.show();
			}
		});

		mTransportText = (TextView) view.findViewById(R.id.protocol_text);

		mQuickConnectContainer =
				(TextInputLayout) view.findViewById(R.id.quickconnect_field_container);

		mQuickConnectField = (EditText) view.findViewById(R.id.quickconnect_field);
		String oldQuickConnect = savedInstanceState == null ?
				null : savedInstanceState.getString(ARG_QUICKCONNECT_STRING);
		mQuickConnectField.setText(oldQuickConnect == null ? mHost.toString() : oldQuickConnect);
		mQuickConnectField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (!mUriFieldEditInProgress) {
					applyQuickConnectString(s.toString(), mHost.getProtocol());

					mUriFieldEditInProgress = true;
					mUsernameField.setText(mHost.getUsername());
					mHostnameField.setText(mHost.getHostname());
					mPortField.setText(Integer.toString(mHost.getPort()));
					mUriFieldEditInProgress = false;
				}
			}
		});

		mExpandCollapseButton = (ImageButton) view.findViewById(R.id.expand_collapse_button);
		mExpandCollapseButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setUriPartsContainerExpanded(!mIsUriEditorExpanded);
			}
		});

		mUriPartsContainer = view.findViewById(R.id.uri_parts_container);

		mUsernameContainer = view.findViewById(R.id.username_field_container);
		mUsernameField = (EditText) view.findViewById(R.id.username_edit_text);
		mUsernameField.setText(mHost.getUsername());
		mUsernameField.addTextChangedListener(new HostTextFieldWatcher(HostDatabase.FIELD_HOST_USERNAME));

		mHostnameContainer = view.findViewById(R.id.hostname_field_container);
		mHostnameField = (EditText) view.findViewById(R.id.hostname_edit_text);
		mHostnameField.setText(mHost.getHostname());
		mHostnameField.addTextChangedListener(new HostTextFieldWatcher(HostDatabase.FIELD_HOST_HOSTNAME));

		mPortContainer = view.findViewById(R.id.port_field_container);
		mPortField = (EditText) view.findViewById(R.id.port_edit_text);
		mPortField.setText(Integer.toString(mHost.getPort()));
		mPortField.addTextChangedListener(new HostTextFieldWatcher(HostDatabase.FIELD_HOST_PORT));

		mNicknameItem = view.findViewById(R.id.nickname_item);

		setTransportType(mHost.getProtocol(), /* setDefaultPortInModel */ false);

		mNicknameField = (EditText) view.findViewById(R.id.nickname_field);
		mNicknameField.setText(mHost.getNickname());
		mNicknameField.addTextChangedListener(
				new HostTextFieldWatcher(HostDatabase.FIELD_HOST_NICKNAME));

		mColorItem = view.findViewById(R.id.color_item);
		mColorItem.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				PopupMenu menu = new PopupMenu(getActivity(), v);
				for (int i = 0; i < mColorNames.length(); i++) {
					menu.getMenu().add(mColorNames.getText(i));
				}
				menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						for (int i = 0; i < mColorNames.length(); i++) {
							if (item.getTitle().toString().equals(mColorNames.getText(i).toString())) {
								mHost.setColor(mColorValues.getText(i).toString());
								mColorText.setText(mColorNames.getText(i));
								return true;
							}
						}
						return false;
					}
				});
				menu.show();
			}
		});

		mColorText = (TextView) view.findViewById(R.id.color_text);
		for (int i = 0; i < mColorValues.length(); i++) {
			if (mColorValues.getText(i).toString().equals(mHost.getColor())) {
				mColorText.setText(mColorNames.getText(i));
				break;
			}
		}

		mFontSizeText = (EditText) view.findViewById(R.id.font_size_text);
		mFontSizeText.setText(Integer.toString(mHost.getFontSize()));
		mFontSizeText.addTextChangedListener(
				new HostTextFieldWatcher(HostDatabase.FIELD_HOST_FONTSIZE));
		mFontSizeText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (!hasFocus)
					mFontSizeText.setText(Integer.toString(mHost.getFontSize()));
			}
		});

		mFontSizeSeekBar = (SeekBar) view.findViewById(R.id.font_size_bar);
		mFontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				setFontSize(MINIMUM_FONT_SIZE + progress);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				/* Clear focus on font size EditText so its text value can update. */
				mFontSizeText.clearFocus();
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
		mFontSizeSeekBar.setProgress(mHost.getFontSize() - MINIMUM_FONT_SIZE);

		mPubkeyItem = view.findViewById(R.id.pubkey_item);
		mPubkeyItem.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				PopupMenu menu = new PopupMenu(getActivity(), v);
				for (String name : mPubkeyNames) {
					menu.getMenu().add(name);
				}
				menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						for (int i = 0; i < mPubkeyNames.size(); i++) {
							if (mPubkeyNames.get(i).equals(item.getTitle())) {
								mHost.setPubkeyId(Long.parseLong(mPubkeyValues.get(i)));
								mPubkeyText.setText(mPubkeyNames.get(i));
								return true;
							}
						}
						return false;
					}
				});
				menu.show();
			}
		});

		mPubkeyText = (TextView) view.findViewById(R.id.pubkey_text);
		for (int i = 0; i < mPubkeyValues.size(); i++) {
			if (mHost.getPubkeyId() == Long.parseLong(mPubkeyValues.get(i))) {
				mPubkeyText.setText(mPubkeyNames.get(i));
				break;
			}
		}

		mDelKeyItem = view.findViewById(R.id.delkey_item);
		mDelKeyItem.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				PopupMenu menu = new PopupMenu(getActivity(), v);
				for (int i = 0; i < mDelKeyNames.length(); i++) {
					menu.getMenu().add(mDelKeyNames.getText(i));
				}
				menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						for (int i = 0; i < mDelKeyNames.length(); i++) {
							if (mDelKeyNames.getText(i).equals(item.getTitle())) {
								mHost.setDelKey(mDelKeyValues.getText(i).toString());
								mDelKeyText.setText(mDelKeyNames.getText(i));
								return true;
							}
						}
						return false;
					}
				});
				menu.show();
			}
		});

		mDelKeyText = (TextView) view.findViewById(R.id.delkey_text);
		for (int i = 0; i < mDelKeyValues.length(); i++) {
			if (mDelKeyValues.getText(i).toString().equals(mHost.getDelKey())) {
				mDelKeyText.setText(mDelKeyNames.getText(i));
				break;
			}
		}

		mEncodingItem = view.findViewById(R.id.encoding_item);
		mEncodingItem.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				PopupMenu menu = new PopupMenu(getActivity(), v);
				for (String displayName : mCharsetData.keySet()) {
					menu.getMenu().add(displayName);
				}
				menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						for (String displayName : mCharsetData.keySet()) {
							if (displayName.equals(item.getTitle())) {
								mHost.setEncoding(mCharsetData.get(displayName));
								mEncodingText.setText(displayName);
								return true;
							}
						}
						return false;
					}
				});
				menu.show();
			}
		});

		// The encoding text is initialized in setCharsetData() because Charset data is not always
		// available when this fragment is created.
		mEncodingText = (TextView) view.findViewById(R.id.encoding_text);

		mUseSshAuthItem = view.findViewById(R.id.use_ssh_auth_item);
		mUseSshAuthItem.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mUseSshAuthSwitch.toggle();
			}
		});

		mUseSshAuthSwitch = (SwitchCompat) view.findViewById(R.id.use_ssh_auth_switch);
		mUseSshAuthSwitch.setChecked(mHost.getUseAuthAgent() != null &&
				!mHost.getUseAuthAgent().equals(mSshAuthValues.getString(0)));
		mUseSshAuthSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				processSshAuthChange();
			}
		});

		mUseSshConfirmationItem = view.findViewById(R.id.ssh_auth_confirmation_item);
		mUseSshConfirmationItem.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mUseSshConfirmationCheckbox.toggle();
			}
		});

		mUseSshConfirmationCheckbox =
				(AppCompatCheckBox) view.findViewById(R.id.ssh_auth_confirmation_checkbox);
		mUseSshConfirmationCheckbox.setChecked(mHost.getUseAuthAgent() != null &&
				mHost.getUseAuthAgent().equals(mSshAuthValues.getString(1)));
		mUseSshConfirmationCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				processSshAuthChange();
			}
		});

		processSshAuthChange();

		mCompressionSwitch = (CheckableMenuItem) view.findViewById(R.id.compression_item);
		mCompressionSwitch.setChecked(mHost.getCompression());
		mCompressionSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mHost.setCompression(isChecked);
				handleHostChange();
			}
		});

		mStartShellSwitch = (CheckableMenuItem) view.findViewById(R.id.start_shell_item);
		mStartShellSwitch.setChecked(mHost.getWantSession());
		mStartShellSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mHost.setWantSession(isChecked);
				handleHostChange();
			}
		});

		mStayConnectedSwitch = (CheckableMenuItem) view.findViewById(R.id.stay_connected_item);
		mStayConnectedSwitch.setChecked(mHost.getStayConnected());
		mStayConnectedSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mHost.setStayConnected(isChecked);
				handleHostChange();
			}
		});

		mCloseOnDisconnectSwitch = (CheckableMenuItem) view.findViewById(R.id.close_on_disconnect_item);
		mCloseOnDisconnectSwitch.setChecked(mHost.getQuickDisconnect());
		mCloseOnDisconnectSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mHost.setQuickDisconnect(isChecked);
				handleHostChange();
			}
		});

		mPostLoginAutomationField = (EditText) view.findViewById(R.id.post_login_automation_field);
		mPostLoginAutomationField.setText(mHost.getPostLogin());
		mPostLoginAutomationField.addTextChangedListener(
				new HostTextFieldWatcher(HostDatabase.FIELD_HOST_POSTLOGIN));

		setUriPartsContainerExpanded(mIsUriEditorExpanded);

		return view;
	}

	/**
	 * @param protocol The protocol to set.
	 * @param setDefaultPortInModel True if the model's port should be updated to the default port
	 *     for the given protocol.
	 */
	private void setTransportType(String protocol, boolean setDefaultPortInModel) {
		mHost.setProtocol(protocol);
		if (setDefaultPortInModel)
			mHost.setPort(TransportFactory.getTransport(protocol).getDefaultPort());
		handleHostChange();

		mTransportText.setText(protocol);

		mQuickConnectContainer.setHint(
				TransportFactory.getFormatHint(protocol, getActivity()));

		// Different protocols have different field types, so show only the fields needed.
		if (SSH.getProtocolName().equals(protocol)) {
			mUsernameContainer.setVisibility(View.VISIBLE);
			mHostnameContainer.setVisibility(View.VISIBLE);
			mPortContainer.setVisibility(View.VISIBLE);
			mExpandCollapseButton.setVisibility(View.VISIBLE);
			mNicknameItem.setVisibility(View.VISIBLE);
		} else if (Telnet.getProtocolName().equals(protocol)) {
			mUsernameContainer.setVisibility(View.GONE);
			mHostnameContainer.setVisibility(View.VISIBLE);
			mPortContainer.setVisibility(View.VISIBLE);
			mExpandCollapseButton.setVisibility(View.VISIBLE);
			mNicknameItem.setVisibility(View.VISIBLE);
		} else {
			// Local protocol has only one field, so no need to show the URI parts
			// container.
			setUriPartsContainerExpanded(false);
			mExpandCollapseButton.setVisibility(View.GONE);
			mNicknameItem.setVisibility(View.GONE);
		}
	}

	private void setFontSize(int fontSize) {
		if (fontSize < MINIMUM_FONT_SIZE)
			fontSize = MINIMUM_FONT_SIZE;

		if (fontSize > MAXIMUM_FONT_SIZE)
			fontSize = MAXIMUM_FONT_SIZE;

		mHost.setFontSize(fontSize);

		if (mFontSizeSeekBar.getProgress() + MINIMUM_FONT_SIZE != fontSize) {
			mFontSizeSeekBar.setProgress(fontSize - MINIMUM_FONT_SIZE);
		}

		if (!mFontSizeText.isFocused() &&
				Integer.parseInt(mFontSizeText.getText().toString()) != fontSize) {
			mFontSizeText.setText(Integer.toString(fontSize));
		}

		handleHostChange();
	}

	private void processSshAuthChange() {
		mUseSshConfirmationItem.setVisibility(
				mUseSshAuthSwitch.isChecked() ? View.VISIBLE : View.GONE);

		if (mUseSshAuthSwitch.isChecked()) {
			mHost.setUseAuthAgent(
					mUseSshConfirmationCheckbox.isChecked() ?
									/* require confirmation */ mSshAuthValues.getString(1) :
									/* don't require confirmation */ mSshAuthValues.getString(2));
		} else {
			mHost.setUseAuthAgent(/* don't use */ mSshAuthValues.getString(0));
		}

		handleHostChange();
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			mListener = (Listener) context;
		} catch (ClassCastException e) {
			throw new ClassCastException(context.toString() + " must implement Listener");
		}

		// Now that the fragment is attached to an Activity, fetch the arrays from the attached
		// Activity's resources.
		mColorNames = getResources().obtainTypedArray(R.array.list_colors);
		mColorValues = getResources().obtainTypedArray(R.array.list_color_values);
		mSshAuthNames = getResources().obtainTypedArray(R.array.list_authagent);
		mSshAuthValues = getResources().obtainTypedArray(R.array.list_authagent_values);
		mDelKeyNames = getResources().obtainTypedArray(R.array.list_delkey);
		mDelKeyValues = getResources().obtainTypedArray(R.array.list_delkey_values);
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
		mColorNames.recycle();
		mColorValues.recycle();
		mSshAuthNames.recycle();
		mSshAuthValues.recycle();
		mDelKeyNames.recycle();
		mDelKeyValues.recycle();
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);

		savedInstanceState.putLong(ARG_EXISTING_HOST_ID, mHost.getId());
		savedInstanceState.putParcelable(ARG_EXISTING_HOST, mHost.getValues());
		savedInstanceState.putBoolean(ARG_IS_EXPANDED, mIsUriEditorExpanded);
		savedInstanceState.putString(
				ARG_QUICKCONNECT_STRING, mQuickConnectField.getText().toString());
		savedInstanceState.putStringArrayList(ARG_PUBKEY_NAMES, mPubkeyNames);
		savedInstanceState.putStringArrayList(ARG_PUBKEY_VALUES, mPubkeyValues);
	}

	/**
	 * Sets the Charset encoding data for the editor.
	 * @param data A map from Charset display name to Charset value (i.e., unique ID for the
	 *     Charset).
	 */
	public void setCharsetData(final Map<String, String> data) {
		mCharsetData = data;

		if (mEncodingText != null) {
			Iterator it = data.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, String> pair = (Map.Entry) it.next();
				if (pair.getValue().equals(mHost.getEncoding())) {
					mEncodingText.setText(pair.getKey());
					return;
				}
			}
		}
	}

	private void setUriPartsContainerExpanded(boolean expanded) {
		mIsUriEditorExpanded = expanded;

		if (mIsUriEditorExpanded) {
			mExpandCollapseButton.setImageResource(R.drawable.ic_expand_less);
			mUriPartsContainer.setVisibility(View.VISIBLE);
		} else {
			mExpandCollapseButton.setImageResource(R.drawable.ic_expand_more);
			mUriPartsContainer.setVisibility(View.GONE);
		}
	}

	/**
	 * Applies the quick-connect URI entered in the field by copying its URI parts to mHost's
	 * fields.
	 * @param quickConnectString The URI entered in the quick-connect field.
	 * @param protocol The protocol for this connection.
	 */
	private void applyQuickConnectString(String quickConnectString, String protocol) {
		if (quickConnectString == null || protocol == null)
			return;

		Uri uri = TransportFactory.getUri(protocol, quickConnectString);
		if (uri == null) {
			// If the URI was invalid, null out the associated fields.
			mHost.setProtocol(protocol);
			mHost.setUsername(null);
			mHost.setHostname(null);
			mHost.setNickname(null);
			mHost.setPort(TransportFactory.getTransport(protocol).getDefaultPort());
			return;
		}

		HostBean host = TransportFactory.getTransport(protocol).createHost(uri);
		mHost.setProtocol(host.getProtocol());
		mHost.setUsername(host.getUsername());
		mHost.setHostname(host.getHostname());
		mHost.setNickname(host.getNickname());
		mHost.setPort(host.getPort());
		handleHostChange();
	}

	/**
	 * Handles a change in the host caused by the user adjusting the values of one of the widgets
	 * in this fragment. If the change has resulted in a valid host, the new value is sent back
	 * to the listener; however, if the change ha resulted in an invalid host, the listener is
	 * notified.
	 */
	private void handleHostChange() {
		String quickConnectString = mQuickConnectField.getText().toString();
		if (quickConnectString == null || quickConnectString.equals("")) {
			// Invalid protocol and/or string, so don't do anything.
			mListener.onHostInvalidated();
			return;
		}

		Uri uri = TransportFactory.getUri(mHost.getProtocol(), quickConnectString);
		if (uri == null) {
			// Valid string, but does not accurately describe a URI.
			mListener.onHostInvalidated();
			return;
		}

		// Now, the host is confirmed to have a valid URI.
		mListener.onValidHostConfigured(mHost);
	}

	public interface Listener {
		void onValidHostConfigured(HostBean host);
		void onHostInvalidated();
	}

	private class HostTextFieldWatcher implements TextWatcher {

		private final String mFieldType;

		public HostTextFieldWatcher(String fieldType) {
			mFieldType = fieldType;
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}

		@Override
		public void afterTextChanged(Editable s) {
			String text = s.toString();

			if (HostDatabase.FIELD_HOST_USERNAME.equals(mFieldType)) {
				mHost.setUsername(text);
			} else if (HostDatabase.FIELD_HOST_HOSTNAME.equals(mFieldType)) {
				mHost.setHostname(text);
			} else if (HostDatabase.FIELD_HOST_PORT.equals(mFieldType)) {
				try {
					mHost.setPort(Integer.parseInt(text));
				} catch (NumberFormatException e) {
					return;
				}
			} else if (HostDatabase.FIELD_HOST_NICKNAME.equals(mFieldType)) {
				mHost.setNickname(text);
			} else if (HostDatabase.FIELD_HOST_POSTLOGIN.equals(mFieldType)) {
				mHost.setPostLogin(text);
			} else if (HostDatabase.FIELD_HOST_FONTSIZE.equals(mFieldType)) {
				int fontSize = HostBean.DEFAULT_FONT_SIZE;
				try {
					fontSize = Integer.parseInt(text);
				} catch (NumberFormatException e) {
				} finally {
					setFontSize(fontSize);
				}
			} else {
				throw new RuntimeException("Invalid field type.");
			}

			if (isUriRelatedField(mFieldType)) {
				mNicknameField.setText(mHost.toString());
				mHost.setNickname(mHost.toString());

				if (!mUriFieldEditInProgress) {
					mUriFieldEditInProgress = true;
					mQuickConnectField.setText(mHost.toString());
					mUriFieldEditInProgress = false;
				}
			}
			handleHostChange();
		}

		private boolean isUriRelatedField(String fieldType) {
			return HostDatabase.FIELD_HOST_USERNAME.equals(fieldType) ||
					HostDatabase.FIELD_HOST_HOSTNAME.equals(fieldType) ||
					HostDatabase.FIELD_HOST_PORT.equals(fieldType);
		}
	}
}
