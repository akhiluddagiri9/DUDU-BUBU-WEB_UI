import { Component, OnInit, computed, inject, output, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { Observable } from 'rxjs';

interface Profile {
  name: string;
  email: string;
  pictureUrl: string | null;
}

const ACCOUNT_URL = '/api/account';
const WRITE_OPTIONS = { headers: { 'X-Requested-With': 'XMLHttpRequest' } };
const MAX_PICTURE_BYTES = 768 * 1024;

@Component({
  selector: 'app-account',
  imports: [FormsModule],
  templateUrl: './account.component.html',
  styleUrl: './account.component.css'
})
export class AccountComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly signedOut = output<void>();
  readonly user = signal<Profile | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly registering = signal(false);
  readonly error = signal('');
  readonly status = signal('');
  readonly initial = computed(() => Array.from(this.user()?.name ?? '')[0]?.toUpperCase() || '?');
  readonly title = computed(() => {
    if (this.user()) return 'Your profile';
    return this.registering() ? 'Create an account' : 'Sign in';
  });

  ngOnInit(): void {
    this.http.get<Profile>(ACCOUNT_URL).subscribe({
      next: profile => {
        this.user.set(profile);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  open(dialog: HTMLDialogElement): void {
    if (!this.busy()) {
      this.clearFeedback();
    }
    dialog.showModal();
  }

  switchMode(form: NgForm): void {
    this.registering.update(registering => !registering);
    form.resetForm({ email: form.value.email ?? '' });
    this.clearFeedback();
  }

  authenticate(form: NgForm): void {
    if (form.invalid || this.busy()) {
      form.control.markAllAsTouched();
      return;
    }
    const action = this.registering() ? 'register' : 'login';
    this.updateProfile(
      this.http.post<Profile>(`${ACCOUNT_URL}/${action}`, form.value, WRITE_OPTIONS),
      'Could not connect to the account service. Try again.',
      action === 'register' ? 'Your account is ready.' : 'You signed in.',
      () => {
        form.resetForm();
        this.registering.set(false);
      }
    );
  }

  uploadPicture(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.busy()) return;
    this.clearFeedback();
    if (file.size > MAX_PICTURE_BYTES) {
      this.error.set('Choose a picture under 768 KB.');
      return;
    }
    const body = new FormData();
    body.append('picture', file);
    this.updateProfile(
      this.http.put<Profile>(`${ACCOUNT_URL}/picture`, body, WRITE_OPTIONS),
      'Could not upload your picture. Try a JPEG or PNG under 768 KB.',
      'Profile picture updated.'
    );
  }

  removePicture(): void {
    this.updateProfile(
      this.http.delete<Profile>(`${ACCOUNT_URL}/picture`, WRITE_OPTIONS),
      'Could not remove your picture. Try again.',
      'Profile picture removed.'
    );
  }

  logout(dialog: HTMLDialogElement): void {
    if (this.busy()) return;
    this.clearFeedback();
    this.busy.set(true);
    this.http.post<void>(`${ACCOUNT_URL}/logout`, null, WRITE_OPTIONS).subscribe({
      next: () => {
        this.user.set(null);
        this.busy.set(false);
        dialog.close();
        this.signedOut.emit();
      },
      error: (response: HttpErrorResponse) => this.handleError(response, 'Could not sign out. Try again.')
    });
  }

  private updateProfile(
    request: Observable<Profile>,
    fallbackError: string,
    successMessage: string,
    onSuccess?: () => void
  ): void {
    if (this.busy()) return;
    this.clearFeedback();
    this.busy.set(true);
    request.subscribe({
      next: profile => {
        onSuccess?.();
        this.user.set(profile);
        this.status.set(successMessage);
        this.busy.set(false);
      },
      error: (response: HttpErrorResponse) => this.handleError(response, fallbackError)
    });
  }

  private handleError(response: HttpErrorResponse, fallback: string): void {
    if (response.status === 401 && this.user()) {
      this.user.set(null);
    }
    const message: unknown = response.error?.error;
    this.error.set(typeof message === 'string' ? message : fallback);
    this.busy.set(false);
  }

  private clearFeedback(): void {
    this.error.set('');
    this.status.set('');
  }
}
