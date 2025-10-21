import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import {MatToolbar, MatToolbarModule,} from '@angular/material/toolbar';
import {MatButton, MatButtonModule, MatIconButton} from '@angular/material/button';
import {MatFormField, MatFormFieldModule, MatLabel} from '@angular/material/form-field';
import {MatInput, MatInputModule} from '@angular/material/input';
import {MatTable, MatTableModule} from '@angular/material/table';
import {MatIcon, MatIconModule} from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';  
import { MatTooltipModule } from '@angular/material/tooltip';  
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';  
import { MatSnackBarModule } from '@angular/material/snack-bar';  
import { MatDividerModule } from '@angular/material/divider';

@NgModule({
  declarations: [],
  imports: [
    MatToolbarModule,
    CommonModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    MatIconModule,
    MatCardModule,  
    MatTooltipModule,  
    MatProgressSpinnerModule,  
    MatSnackBarModule,
    MatDividerModule,
  ],
   exports: [
    MatToolbarModule,  
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    MatIconModule,
    MatCardModule,  
    MatTooltipModule,  
    MatProgressSpinnerModule,  
    MatSnackBarModule,
    MatDividerModule,
  ]
})
export class MaterialModule { }
