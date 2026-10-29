package com.virditech.ac7000.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;

import com.virditech.ac7000.R;
import com.virditech.ac7000.databinding.DialogEvaluationConfigBinding;
import com.virditech.ac7000.evaluation.EvaluationConditions;

import java.util.List;
import java.util.function.Consumer;

public final class EvaluationConfigDialog {
    private final Activity activity;
    private AlertDialog dialog;

    public EvaluationConfigDialog(Activity activity) { this.activity = activity; }

    public void show(List<String> labels, Consumer<EvaluationConditions> onStart, Runnable onDismiss) {
        DialogEvaluationConfigBinding binding =
                DialogEvaluationConfigBinding.inflate(activity.getLayoutInflater());
        ArrayAdapter<String> classes = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, labels);
        classes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.groundTruth.setAdapter(classes);
        ArrayAdapter<String> sectors = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item,
                new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9"});
        sectors.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.sector.setAdapter(sectors);
        AlertDialog current = new AlertDialog.Builder(activity)
                .setTitle(R.string.evaluation_title).setView(binding.getRoot())
                .setPositiveButton(R.string.evaluation_start, null)
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog = current;
        current.setOnDismissListener(ignored -> {
            if (dialog == current) dialog = null;
            onDismiss.run();
        });
        current.setOnShowListener(ignored -> current.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        double distance;
                        try {
                            distance = Double.parseDouble(binding.distanceCm.getText().toString().trim());
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Enter distance in cm");
                        }
                        EvaluationConditions conditions = new EvaluationConditions(
                                binding.groundTruth.getSelectedItemPosition(),
                                binding.subjectId.getText().toString(),
                                binding.attackMediumId.getText().toString(),
                                binding.sector.getSelectedItemPosition() + 1, distance,
                                binding.lighting.getText().toString());
                        current.dismiss();
                        onStart.accept(conditions);
                    } catch (IllegalArgumentException e) {
                        binding.error.setText(e.getMessage());
                        binding.error.setVisibility(View.VISIBLE);
                    }
                }));
        current.show();
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }
}
