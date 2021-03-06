/*****************************************************************
 *   Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/

package org.apache.cayenne.modeler.dialog.codegen;

import org.apache.cayenne.gen.ClassGenerationAction;
import org.apache.cayenne.map.DataMap;
import org.apache.cayenne.modeler.CodeTemplateManager;
import org.apache.cayenne.modeler.dialog.pref.PreferenceDialog;
import org.apache.cayenne.modeler.pref.DataMapDefaults;
import org.apache.cayenne.swing.BindingBuilder;
import org.apache.cayenne.swing.ObjectBinding;
import org.apache.cayenne.util.Util;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import java.awt.Component;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static org.apache.cayenne.modeler.CodeTemplateManager.SINGLE_SERVER_CLASS;
import static org.apache.cayenne.modeler.CodeTemplateManager.STANDARD_SERVER_SUBCLASS;
import static org.apache.cayenne.modeler.CodeTemplateManager.STANDARD_SERVER_SUPERCLASS;
import static org.apache.cayenne.modeler.dialog.pref.PreferenceDialog.TEMPLATES_KEY;

/**
 * A controller for the custom generation mode.
 */
public class CustomModeController extends GeneratorController {

	// correspond to non-public constants on MapClassGenerator.
	private static final String MODE_ENTITY = "entity";

	protected CustomModePanel view;
	private CodeTemplateManager templateManager;

	private ObjectBinding superTemplate;
	private ObjectBinding subTemplate;

	private CustomPreferencesUpdater preferencesUpdater;

	public CustomPreferencesUpdater getCustomPreferencesUpdater() {
		return preferencesUpdater;
	}

	public CustomModeController(CodeGeneratorControllerBase parent) {
		super(parent);

		// bind preferences and init defaults...

		Set<Entry<DataMap, DataMapDefaults>> entities = getMapPreferences().entrySet();

		for (Entry<DataMap, DataMapDefaults> entry : entities) {

			if (Util.isEmptyString(entry.getValue().getSuperclassTemplate())) {
				entry.getValue().setSuperclassTemplate(STANDARD_SERVER_SUPERCLASS);
			}

			if (Util.isEmptyString(entry.getValue().getSubclassTemplate())) {
				entry.getValue().setSubclassTemplate(STANDARD_SERVER_SUBCLASS);
			}

			if (Util.isEmptyString(entry.getValue().getProperty("mode"))) {
				entry.getValue().setProperty("mode", MODE_ENTITY);
			}

			if (Util.isEmptyString(entry.getValue().getProperty("overwrite"))) {
				entry.getValue().setBooleanProperty("overwrite", false);
			}

			if (Util.isEmptyString(entry.getValue().getProperty("pairs"))) {
				entry.getValue().setBooleanProperty("pairs", true);
			}

			if (Util.isEmptyString(entry.getValue().getProperty("usePackagePath"))) {
				entry.getValue().setBooleanProperty("usePackagePath", true);
			}

			if (Util.isEmptyString(entry.getValue().getProperty("outputPattern"))) {
				entry.getValue().setProperty("outputPattern", "*.java");
			}
		}

		BindingBuilder builder = new BindingBuilder(getApplication().getBindingFactory(), this);

		builder.bindToAction(view.getManageTemplatesLink(), "popPreferencesAction()");

		builder.bindToStateChange(view.getOverwrite(), "customPreferencesUpdater.overwrite").updateView();

		builder.bindToStateChange(view.getPairs(), "customPreferencesUpdater.pairs").updateView();

		builder.bindToStateChange(view.getUsePackagePath(), "customPreferencesUpdater.usePackagePath").updateView();

		subTemplate = builder.bindToComboSelection(view.getSubclassTemplate(),
				"customPreferencesUpdater.subclassTemplate");

		superTemplate = builder.bindToComboSelection(view.getSuperclassTemplate(),
				"customPreferencesUpdater.superclassTemplate");

		builder.bindToTextField(view.getOutputPattern(), "customPreferencesUpdater.outputPattern").updateView();

		builder.bindToStateChange(view.getCreatePropertyNames(), "customPreferencesUpdater.createPropertyNames")
				.updateView();

		builder.bindToStateChange(view.getCreatePKProperties(), "customPreferencesUpdater.createPKProperties")
				.updateView();

		updateTemplates();
	}

	protected void createDefaults() {
		TreeMap<DataMap, DataMapDefaults> map = new TreeMap<DataMap, DataMapDefaults>();
		Collection<DataMap> dataMaps = getParentController().getDataMaps();
		for (DataMap dataMap : dataMaps) {
			DataMapDefaults preferences;
			preferences = getApplication().getFrameController().getProjectController()
					.getDataMapPreferences(this.getClass().getName().replace(".", "/"), dataMap);
			preferences.setSuperclassPackage("");
			preferences.updateSuperclassPackage(dataMap, false);

			map.put(dataMap, preferences);

			if (getOutputPath() == null) {
				setOutputPath(preferences.getOutputPath());
			}
		}

		setMapPreferences(map);
		preferencesUpdater = new CustomPreferencesUpdater(map);
	}

	protected GeneratorControllerPanel createView() {
		this.view = new CustomModePanel();
		return view;
	}

	private void updateTemplates() {
		this.templateManager = getApplication().getCodeTemplateManager();

		List<String> customTemplates = new ArrayList<>(templateManager.getCustomTemplates().keySet());
		Collections.sort(customTemplates);

		List<String> superTemplates = new ArrayList<>(templateManager.getStandardSuperclassTemplates());
		Collections.sort(superTemplates);
		superTemplates.addAll(customTemplates);

		List<String> subTemplates = new ArrayList<>(templateManager.getStandardSubclassTemplates());
		Collections.sort(subTemplates);
		subTemplates.addAll(customTemplates);

		this.view.getSubclassTemplate().setModel(new DefaultComboBoxModel<>(subTemplates.toArray(new String[0])));
		this.view.getSuperclassTemplate().setModel(new DefaultComboBoxModel<>(superTemplates.toArray(new String[0])));

		JCheckBox pairs = this.view.getPairs();
		updateView();
		pairs.addItemListener(e -> updateView());

		superTemplate.updateView();
		subTemplate.updateView();
	}

	private void updateView() {
		boolean selected = view.getPairs().isSelected();
		JComboBox<String> subclassTemplate = view.getSubclassTemplate();
		subclassTemplate.setSelectedItem(selected ? STANDARD_SERVER_SUBCLASS : SINGLE_SERVER_CLASS);
		view.getSuperclassTemplate().setEnabled(selected);
		view.getOverwrite().setEnabled(!selected);
	}

	public Component getView() {
		return view;
	}

	public Collection<ClassGenerationAction> createGenerator() {

		Collection<ClassGenerationAction> generators = super.createGenerator();

		String superKey = Objects.requireNonNull(view.getSuperclassTemplate().getSelectedItem()).toString();
		String superTemplate = templateManager.getTemplatePath(superKey);

		String subKey = Objects.requireNonNull(view.getSubclassTemplate().getSelectedItem()).toString();
		String subTemplate = templateManager.getTemplatePath(subKey);

		for (ClassGenerationAction generator : generators) {
			generator.setSuperTemplate(superTemplate);
			generator.setTemplate(subTemplate);
			generator.setOverwrite(view.getOverwrite().isSelected());
			generator.setUsePkgPath(view.getUsePackagePath().isSelected());
			generator.setMakePairs(view.getPairs().isSelected());
			generator.setCreatePropertyNames(view.getCreatePropertyNames().isSelected());
			generator.setCreatePKProperties(view.getCreatePKProperties().isSelected());

			if (!Util.isEmptyString(view.getOutputPattern().getText())) {
				generator.setOutputPattern(view.getOutputPattern().getText());
			}
		}

		return generators;
	}

	public void popPreferencesAction() {
		new PreferenceDialog(getApplication().getFrameController()).startupAction(TEMPLATES_KEY);
		updateTemplates();
	}

	@Override
	protected ClassGenerationAction newGenerator() {
		ClassGenerationAction action = new ClassGenerationAction();
		getApplication().getInjector().injectMembers(action);
		return action;
	}
}
